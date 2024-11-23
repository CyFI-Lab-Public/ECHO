import os, sys
import json 
import argparse


# get current file path
current_path = os.path.dirname(os.path.abspath(__file__))
TEMPLATE_FOLDER_PATH = os.path.join(current_path, 'templates')


with open(os.path.join(current_path, 'sink_api_category.json'), 'r') as f:
    tmp = json.load(f)

SINK_API_CATEGORY_MAP = {a['method_signature']: a for a in tmp}


def parse_arg():
    parser = argparse.ArgumentParser(prog="Remediate Template Generation", description='Generate remediate template for a given graph')
    parser.add_argument('--graph_file_path', type=str, help='Graph file path from graph builder.', required=True)
    parser.add_argument('--output_path', type=str, help='Output folder path to store the generated templates.', required=True)
    parser.add_argument('--package_name', type=str, help='Type of template to generate', required=True)

    return parser.parse_args()


def generate_tempalte(eptype, interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path):
    if eptype == "#Execute":
        return generate_ce_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path)
    elif eptype == '#Webview':
        return generate_webview_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path)
    elif eptype == "#Toast":
        return generate_toast_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path)
    elif eptype == "#Intent":
        return generate_intent_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path)
    elif eptype == "#Stop":
        return generate_terminate_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path)
    return None

def generate_ce_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path):
    with open(os.path.join(TEMPLATE_FOLDER_PATH, 'JSICmdExecutionTemplate.js'), 'r') as f:
        res = f.read()
        res = res.replace("PKG_NAME", packagename)
        res = res.replace("INTERFACE_NAME", interface_name)
        res = res.replace("EP_NAME", epname)
        
        arg_pattern_nu = get_arg_pattern(arg_pos, arg_cnt, "cmdNotifyUser")
        arg_pattern_da = get_arg_pattern(arg_pos, arg_cnt, "cmdDelete")
        res = res.replace("ARG_PATTERN_NOTIFY_USER", arg_pattern_nu)
        res = res.replace("ARG_PATTERN_DELETE_APP", arg_pattern_da)
        
    print('generated template result available at: ' + output_file_path)
    with open(output_file_path, 'w') as f:
        f.write(res)
        return None

def generate_webview_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path):
    with open(os.path.join(TEMPLATE_FOLDER_PATH, 'JSIWebviewTemplate.js'), 'r') as f:
        res = f.read()
        res = res.replace("PKG_NAME", packagename)
        res = res.replace("INTERFACE_NAME", interface_name)
        res = res.replace("EP_NAME", epname)
        
        arg_pattern_nu = get_arg_pattern(arg_pos, arg_cnt, "urlToNotifyUser")
        res = res.replace("ARG_PATTERN", arg_pattern_nu)

        
    with open(output_file_path, 'w') as f:
        f.write(res)
        return None
    

def generate_toast_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path):
    
    with open(os.path.join(TEMPLATE_FOLDER_PATH, 'JSIToastTemplate.js'), 'r') as f:
        res = f.read()
        res = res.replace("PKG_NAME", packagename)
        res = res.replace("INTERFACE_NAME", interface_name)
        res = res.replace("EP_NAME", epname)
        
        arg_pattern_nu = get_arg_pattern(arg_pos, arg_cnt, "messageToUser")
        res = res.replace("ARG_PATTERN", arg_pattern_nu)

        
    with open(output_file_path, 'w') as f:
        f.write(res)
        return None
    
    

def generate_terminate_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path):
    
    with open(os.path.join(TEMPLATE_FOLDER_PATH, 'JSITerminationTemplate.js'), 'r') as f:
        res = f.read()
        res = res.replace("PKG_NAME", packagename)
        res = res.replace("INTERFACE_NAME", interface_name)
        res = res.replace("EP_NAME", epname)
        
        arg_pattern_nu = get_arg_pattern(arg_pos, arg_cnt, "\"\"")
        res = res.replace("ARG_PATTERN", arg_pattern_nu)

        
    with open(output_file_path, 'w') as f:
        f.write(res)
        return None
    
    
def generate_intent_template(interface_name, epname, packagename, arg_pos, arg_cnt, output_file_path):

    with open(os.path.join(TEMPLATE_FOLDER_PATH, 'JSIIntentTemplate.js'), 'r') as f:
        res = f.read()
        res = res.replace("PKG_NAME", packagename)
        res = res.replace("INTERFACE_NAME", interface_name)
        res = res.replace("EP_NAME", epname)
        
        arg_pattern_nu = get_arg_pattern(arg_pos, arg_cnt, "urlToNotifyUser")
        
        res = res.replace("ARG_PATTERN_URL_ONLY", arg_pattern_nu)

        
    with open(output_file_path, 'w') as f:
        f.write(res)
        return None

def get_arg_pattern(arg_pos, arg_cnt, arg_to_fill):
    res = ""
    arg = []
    for i in range(arg_cnt):
        if i == arg_pos:
            arg.append(arg_to_fill) 
        else:
            arg.append("\"\"")
    return ",".join(arg)


def get_category_from_sink_method(sink_method_api):
    if sink_method_api not in SINK_API_CATEGORY_MAP:
        return ""
    else :
        return SINK_API_CATEGORY_MAP[sink_method_api]['category']
    

def get_key_arg_position_from_sink_method(sink_method_api):
    if sink_method_api not in SINK_API_CATEGORY_MAP:
        return -2
    else:
        return SINK_API_CATEGORY_MAP[sink_method_api]['key_parameter_position']
    
    
    
def get_method_name_from_method_signature(jsi_api_signature):
    if jsi_api_signature == "":
        return ""
    return jsi_api_signature.split("(")[0].split(" ")[-1].strip()
    
    
    
    
def resolve_graph(graph_file_path, pkg_name):
    results = []
    if not os.path.exists(graph_file_path):
        return results
    try:
        with open(graph_file_path, 'r') as f:
            data = json.load(f)
    
        for path in data:
            if 'influences' not in path:
                continue
            if type(path['influences']) != list:
                continue
            influences = path['influences']
            
            for influence in influences:
                if 'type' not in influence or influence['type'] != 'webview interface' or 'interfaceMethods' not in influence or type(influence['interfaceMethods']) != list:
                    continue
                
                influence_methods = influence['interfaceMethods'] 
                for influence_method in influence_methods:
                    if 'sourceMethodSignature' not in influence_method:
                        continue
                    sink_method_api_dict = influence_method['sinkMethods']
                    
                    for sink_method_api in sink_method_api_dict:
                        result = {}
                        result['interface_name'] = influence_method['interface_name']
                        result['epname'] = get_method_name_from_method_signature(influence_method['sourceMethodSignature'])
                        if result['epname'] == "":
                            continue
                        result['output_filepath_name'] = pkg_name + "_" + result['epname'] + '_' + get_method_name_from_method_signature(sink_method_api) +  ".js"
                        
                        result['arg_cnt'] = len(sink_method_api.split("(")[1].split(")")[0].split(","))
                        
                        if 'args' not in sink_method_api_dict[sink_method_api]:
                            continue
                        
                        args = sorted(sink_method_api_dict[sink_method_api]['args'], key=lambda x: x['argIndex'])
                        
                        if len(args) == 0:
                            continue 
                        
                        key_arg_position = get_key_arg_position_from_sink_method(sink_method_api)            
                        
                        if key_arg_position == -2:
                            continue
                        if key_arg_position == -1:
                            result['arg_pos'] = -1
                        else:
                            for arg in args:
                                if arg['argIndex'] == key_arg_position:
                                    if 'argSources' not in arg:
                                        continue
                                    arg_sources = arg['argSources']
                                    # We only get 1st one here, but if there is multiple, this needs to be reviewed manually 
                                    if len(arg_sources) == 0:
                                        continue
                                    if len(arg_sources) > 1:
                                        result['note'] = "There are multiple sources for the key argument"
                                    arg_source = arg_sources[0]            
                                    result['arg_pos'] = arg_source['sourceIndex']
                            
                        if 'arg_pos' not in result:
                            continue    
                        results.append(result)    
        return results
    except Exception as e:
        print("Error in reading the graph file")
        print(e)
        return results

def main():
    args = parse_arg()
    resolve_graph_results = resolve_graph(args.graph_file_path, args.package_name)
    if os.path.exists(args.output_path):
        if os.path.isdir(args.output_path):
            pass
        else:
            print("Output path is not a directory")
            return
    else:
        os.makedirs(args.output_path)
    
    print('Generating templates, number of graph results: ' + str(len(resolve_graph_results)))
    for resolve_graph_result in resolve_graph_results:
        if resolve_graph_result == None:
            print("Error in resolving graph")
            continue
        
        print('Generating template for ' + resolve_graph_result['interface_name'] + " " + resolve_graph_result['epname'])
        
        generate_ce_template(resolve_graph_result['interface_name'], resolve_graph_result['epname'], args.package_name, resolve_graph_result['arg_pos'], resolve_graph_result['arg_cnt'], os.path.join(args.output_path, resolve_graph_result['output_filepath_name']))
    

if __name__ == "__main__":
    print('Start generating templates')
    main()
    
