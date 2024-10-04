# This is a post processing script for extending tyhe gragh building modeule. 
# It takes generated graphs that load the paylaod with webview (e.g., loadUrl), which in-vivo influence is limited to the webview interface methods.

# The script will take the data flow analysis results, which capture the paths between JSI methods to ECHO pre-defined sink methods, and extend the paths with reachable webview interface methods.

import json, os, argparse



def parse_arg():
    parser = argparse.ArgumentParser(prog="Remediate Template Generation", description='Generate remediate template for a given graph')
    parser.add_argument('--graph_file_path', type=str, help='Graph file path from graph builder.', required=True)
    parser.add_argument('--influence_path', type=str, help='Path to the influence file', required=True)
    parser.add_argument('--output_path', type=str, help='Output folder path to store the generated templates.', required=True)
    return parser.parse_args()


def get_influence_for_webview_node(webview_node_dict, influence_file_path = ""):
    if 'interfaceNames' not in webview_node_dict or webview_node_dict['interfaceNames'] == []:
        return None

    with open(influence_file_path, 'r') as f:
        data = json.load(f)
    if type(data) != dict:
        return None
        
    influence_methods = []
    for interface_name_n_class in webview_node_dict['interfaceNames']:
        if len(interface_name_n_class.split(":")) != 2:
            continue
        interface_name, interface_class = interface_name_n_class.split(":")
        im = [data[k] for k in data.keys() if (k.startswith('<' + interface_class) and len(data[k]['sinkMethods']) != 0)]
        for i in im:
            i['interface_name'] = interface_name
        influence_methods.extend(im)        
    return influence_methods



def main():
    args = parse_arg()
    graph_file_path = args.graph_file_path
    influence_path = args.influence_path
    if not os.path.exists(graph_file_path):
        print("Graph file path does not exist.")
        return
    if not os.path.exists(influence_path):
        print("Influence file path does not exist.")
        return
    
    with open(graph_file_path, 'r') as f:
        graph_list = json.load(f)
    
    for graph in graph_list:
    
        if 'vertices' not in graph or 'edges' not in graph:
            print("Invalid graph file.")
            return

        graph['influences'] = []
        
        
        vertices = graph['vertices']
        for v in vertices:
            if v['type'] == 'webview':
                influence_methods = get_influence_for_webview_node(v, influence_path)
                if influence_methods == None:
                    continue
                influence = {'type': "webview interface", 'interfaceMethods': influence_methods, 'vertex': v}
                graph['influences'].append(influence)
                
            if v['type'] == 'reflection':
                influence = {'type': "java code reflection", "reflectionAPI":  v['loadMethodName'], 'vertex': v}
                graph['influences'].append(influence)
                
    print('Writing the output to: ', args.output_path)
    with open(args.output_path, 'w') as f:
        json.dump(graph_list, f)
            
        
    
    


if __name__ == '__main__':
    main()