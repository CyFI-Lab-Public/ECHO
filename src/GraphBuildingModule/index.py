import json
import os, sys

import argparse
from StaticNode import StaticNode
from DynamicNode import DynamicNode


class Edge:
    def __init__(self, from_v, to_v, connection_context) -> None:
        self.from_v = from_v
        self.to_v = to_v
        self.connection_context = connection_context
        
    def to_dict(self):
        return {
            'from': self.from_v.to_dict(),
            'to': self.to_v.to_dict() if self.to_v != None else None,
            'connection_context': self.connection_context
        }
        
    

def init_args():
    parser = argparse.ArgumentParser(prog="Graph Builder", description='Build a graph from dataflow and dynamic vertices')
    parser.add_argument('--df_paths', type=str, help='Path to the dataflow files, seperated by ,', required=True)
    parser.add_argument('--dv_paths', type=str, help='Path to the dynamic vertices files, seperated by ,', required=True)   
    parser.add_argument('--output_path', type=str, help='Path to the output file', required=True)
    parser.add_argument('--output_jsi_of_interest_path', type=str, help='Path to the output file for interested JSI classes', required=True)
    
    return parser.parse_args()


def get_dynamic_vertices(dynamic_vertice_paths):
    if not dynamic_vertice_paths:
        return []
    if type(dynamic_vertice_paths) == str:
        dynamic_vertice_paths = [dynamic_vertice_paths]
        
    dynamic_vertices = []
    if type(dynamic_vertice_paths) == list:
        for path in dynamic_vertice_paths:
            with open(path, 'r') as f:
                data = json.load(f)
            dynamic_vertices.extend(data)
    
    for dv in dynamic_vertices:
        if 'stack' in dv and len(dv['stack']) > 1:
            try:
                dv['line_number'] = int(dv['stack'][1].split(':')[-1])
            except Exception as e:
                dv['line_number'] = -1
        else:
            dv['line_number'] = -1

        new_stack = []
        for p in dv['stack']:
            if  p.startswith('End of '):
                continue
            segs = p.split(':')
            if len(segs) == 3 :
                new_stack.append(segs[0] + ':' + segs[1])
            else:
                new_stack.append(p)
        dv['stack'] = new_stack

    
    return [DynamicNode(dv, dv['line_number']) for dv in dynamic_vertices]

    
def get_static_dataflow_from_source_sink_pairs(dataflow_paths):
    if not dataflow_paths:
        return []
    if type(dataflow_paths) == str:
        dataflow_paths = [dataflow_paths]
    if type(dataflow_paths) == list:
        static_vertices = []
        for path in dataflow_paths:
            with open(path, 'r') as f:
                data = json.load(f)
            static_vertices.extend(data)
        return static_vertices
    return []

def get_static_vertices_from_dataflow_pairs(dataflow_pairs):
    
    static_vertices = set()
    source_to_edge_map = {}
    sink_to_edge_map = {}
    
    for pair in dataflow_pairs:
        if 'path' in pair:
            try:
                ln = int(pair['path'][0].split('!')[-1].replace('ln', ''))
            except Exception as e:
                ln = -1
        else: 
            ln = -1
        
        source_vertex = StaticNode(pair['source'], ln)
        
        static_vertices.add(source_vertex)
        if source_vertex not in source_to_edge_map:
            source_to_edge_map[source_vertex] = []
            
        
        if 'path' in pair:
            try:
                ln = int(pair['path'][-1].split('!')[-1].replace('ln', ''))
            except Exception as e:
                ln = -1
        else: 
            ln = -1
        sink_vertex = StaticNode(pair['sink'], ln)
        static_vertices.add(sink_vertex)
        if sink_vertex not in sink_to_edge_map:
            sink_to_edge_map[sink_vertex] = []
        
        edge = {"source": source_vertex, 
                "sink": sink_vertex, 
                "path": pair['path'], 
                "entryPointClass": pair['entryPointClass']}
        
        source_to_edge_map[source_vertex].append(edge)
        sink_to_edge_map[sink_vertex].append(edge)

    return static_vertices, source_to_edge_map, sink_to_edge_map        

def match(dynamic_node:DynamicNode, static_node: StaticNode):
    if dynamic_node.method != static_node.method:
        return False
    if dynamic_node.line_number != static_node.line_number:
        return False
    if not all([s in dynamic_node.stack for s in static_node.stack]):
        return False
    return True
    
    

def buildGraph():
    
    args = init_args()
    dynamic_vertices_paths = args.dv_paths.split(',') 
    dynamic_nodes = get_dynamic_vertices(dynamic_vertices_paths)
    
    static_vertices_paths = args.df_paths.split(',')
    static_dataflow_pairs = get_static_dataflow_from_source_sink_pairs(static_vertices_paths)

    static_nodes, source_to_df_map, sink_to_df_map = get_static_vertices_from_dataflow_pairs(static_dataflow_pairs)
    
    # network starts with network vertices 
    
    for d in dynamic_nodes:
        for s in static_nodes:
            if match(d, s):
                d.matched_static_vertex = s
                s.matched_dynamic_nodes.add(d)                
                
                
                
    start_dynamic_vertices = list(set([d for d in dynamic_nodes if d.type == 'network' and d.url != ""]))
    
    graph_vertices_paths = []
    graph_edges_paths = []
    for d in start_dynamic_vertices:
        propagate_graph(d, [], [], graph_vertices_paths, graph_edges_paths, set(dynamic_nodes), static_nodes, source_to_df_map, sink_to_df_map)
    
    assert len(graph_vertices_paths) == len(graph_edges_paths)
    
    result_output_dicts = []

    
    for i in range(len(graph_vertices_paths)):
        current_vertices = [d.to_dict() for d in graph_vertices_paths[i]]
        current_edges = graph_edges_paths[i]
        
        result_output_dict = {'vertices': current_vertices, 'edges': current_edges}
        result_output_dicts.append(result_output_dict)

    print('Writing the output to: ', args.output_path)

    with open(args.output_path, 'w') as f:
        json.dump(result_output_dicts, f)
    
    
    jsi_of_interest = []
    
    for i in range(len(graph_vertices_paths)):
        for v in graph_vertices_paths[i]:
            if v.type == 'webview' and v.interfaceNames != []:
                jsi_of_interest.extend([s.split(':')[1] for s in v.interfaceNames]) 
                
                
    with open(args.output_jsi_of_interest_path, 'w') as f:
        json.dump(list(set(jsi_of_interest)), f)
    



def propagate_graph(current_node:DynamicNode, current_vertices = [], current_edges=[], graph_vertices_paths = [], graph_edges_paths = [], dynamic_nodes = [], static_nodes= [], source_to_df_map = {}, sink_to_df_map = {}):
    if current_node.type == "":
        return
    if current_node in current_vertices:
        return

    if current_node.type == 'webview' or current_node.type== 'reflection':
        # this is the end of the path
        current_vertices.append(current_node)
        graph_vertices_paths.append([v for v in current_vertices])  
        current_edges.append(Edge(current_node, None, {"type": "end"}).to_dict())
        graph_edges_paths.append([e for e in current_edges])
        
        current_vertices.pop()
        return 

    current_vertices.append(current_node)
    if current_node.matched_static_vertex != None:
        edges = source_to_df_map.get(current_node.matched_static_vertex, [])
        for edge in edges:
            next_static_node = edge['sink']
            for dn in next_static_node.matched_dynamic_nodes:
                current_edges.append(Edge(current_node, dn, {'type': "dataflow", "static_path": edge['path']}).to_dict())
                propagate_graph(dn, current_vertices, current_edges, graph_vertices_paths, graph_edges_paths, dynamic_nodes, static_nodes, source_to_df_map, sink_to_df_map)
                current_edges.pop()
    
    file_read_node_types = ['fileRead', 'unzip', 'reflection', 'webview']
    
    if current_node.type == 'fileWrite':
        nodes_read_same_fp = [d for d in dynamic_nodes if d.filePath == current_node.filePath and d.type in file_read_node_types]
        for d in nodes_read_same_fp:
            current_edges.append(Edge(current_node, d, {"type": "samefileAccess", "filePath": d.filePath}).to_dict())
            
            propagate_graph(d, current_vertices, current_edges, graph_vertices_paths, graph_edges_paths, dynamic_nodes, static_nodes, source_to_df_map, sink_to_df_map)

            
            current_edges.pop()
            
    if current_node.type == 'jsonInit':
        node_read_same_json = [d for d in dynamic_nodes if d.filePath == current_node.filePath and d.type == 'jsonRead']
        for d in node_read_same_json:
            current_edges.append(Edge(current_node, d, {"type": "sameJsonAccess", "jsonHash": d.filePath}).to_dict())
            propagate_graph(d, current_vertices, current_edges, graph_vertices_paths, graph_edges_paths, dynamic_nodes, static_nodes, source_to_df_map, sink_to_df_map)
            current_edges.pop()
    current_vertices.pop()


if __name__ == '__main__':
    buildGraph()     
    
    
    
    