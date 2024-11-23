
#Done: 1. capture request and response. Save to different folders. Should read ENV variable which indicates the folder to save all parameters

#Done: 2. identify the source of applications. A specific URL can be used to pass the value to this script, it should maintain a dict of {device: sample}.

## Done 3. collect downloaded binaries as payload into the result folder.
import os, json, sys
from urllib import response
from mitmproxy import http
from mitmproxy import ctx
from mitmproxy.net.http.http1.assemble import assemble_request, assemble_response
import hashlib

import subprocess

PROJ_ROOT_FOLDER = os.environ[' PROJ_PATH']
if not PROJ_ROOT_FOLDER in sys.path:
    sys.path.append(PROJ_ROOT_FOLDER)
else:
    print("no proj root folder declared")
    
from ENV import DEVICE_REGISTER_HOST, DYNAMIC_DCL_VALID_RESULT_FOLDER_PATH, MITM_CHECK_DIR, TRAFFIC_LOG_FOLDER, SOCKET_IO_SERVER_HOST

class cyfiMitm:
    def __init__(self, except_domain_list=None):
        if except_domain_list:
            try:
                with open(except_domain_list, 'r') as f:
                    self.except_domain = json.load(f)
            except:
                self.except_domain = []
        else:
            self.except_domain = []
        # self.except_domain.append("192.168.2.118")

        self.testbed_registation = {}
        self.wifi_enable = {}
        

    def enable_wifi(self, testbed_ip):
        print("enable wifi for %s" % testbed_ip)
        self.wifi_enable[testbed_ip] = True
        
    
    def disable_wifi(self, testbed_ip):
        print("disable wifi for %s" % testbed_ip)
        self.wifi_enable[testbed_ip] = False

    
    def register_device(self, testbed_name, testbed_ip, sample_hash):
        print('registered from %s for %s， IP: %s' % (testbed_name, sample_hash, testbed_ip))
        redirections = self.load_redirection_table_from_sample_hash(sample_hash)
        self.testbed_registation[testbed_ip] = {'name': testbed_name, 'sample': sample_hash, 'redirection': redirections}
        
        
    def unregister_device(self, testbed_name, testbed_ip):
        print('unregistered from %s ,IP: %s' % (testbed_name, testbed_ip))

        # self.testbed_registation[testbed_ip] = None
        self.testbed_registation.pop(testbed_ip, None)
    
    def check_available(self, testbed_name, testbed_ip):
        if testbed_ip not in self.testbed_registation:
            return False
        if self.testbed_registation[testbed_ip]['name'] != testbed_name:
            return False
        return True

    def request(self, flow):
        print('start request ' + flow.request.url)
        flow.intercept()

        # print('request ip: %s' % flow.client_conn.peername[0])
        # print('request host: %s' % flow.request.host)
        if flow.request.host in self.except_domain:
            flow.resume()
            return
        
        url = flow.request.url
        ip = flow.client_conn.peername[0]
        # Handdle Commands
        
        if flow.request.host == DEVICE_REGISTER_HOST:
            parameters = flow.request.path.split('?')[0].split('/')[1:]
            print(parameters)
            if len(parameters) < 1: 
                flow.response = http.Response.make(200, json.dumps({'success': False, 'message': 'Invalid Parameter'}, {"Content-Type": "application/json"}))
            elif parameters[0] == 'register':  #http://HOST/register/<testbed_name>/<sample_hash>
                if len(parameters) < 3: 
                    flow.response = http.Response.make(200, json.dumps({'success': False, 'message': 'Invalid Registration Parameter'}, {"Content-Type": "application/json"}))
                else:
                    self.register_device(testbed_name=parameters[1], testbed_ip = ip, sample_hash=parameters[2])
                    flow.response = http.Response.make(200, json.dumps({'success': True}), {"Content-Type": "application/json"})
            
            elif parameters[0] == 'unregister': # http://HOST/unregister/<testbed_name>
                self.unregister_device(testbed_name=parameters[1], testbed_ip = ip)
                flow.response = http.Response.make(200, json.dumps({'success': True}), {"Content-Type": "application/json"})
                
            elif parameters[0] == 'check':   #http://HOST/check/<testbed_name>
                testbed_name = parameters[1]
                print('check ' + testbed_name)
                available = self.check_available(testbed_name, ip)
                check_folder_path = os.path.join(MITM_CHECK_DIR, testbed_name)
                flow.response = http.Response.make(200, json.dumps({'success': available}), {"Content-Type": "application/json"})
                if not os.path.exists(check_folder_path):
                    os.mkdir(check_folder_path)
                    
        
            elif parameters[0] == 'exit':
                flow.response = http.Response.make(200, json.dumps({'success': True}), {"Content-Type": "application/json"})
                ctx.master.shutdown()
            
            elif parameters[0] == 'enable_wifi':
                self.enable_wifi(ip)
                
            elif parameters[0] == 'disable_wifi':
                self.disable_wifi(ip)
        
            else:
                flow.response = http.Response.make(200, json.dumps({'success': False, 'message': 'Invalid Parameter'}), {"Content-Type": "application/json"})
            # flow.resume()
            # return 

        else:
            if  ip in self.wifi_enable and self.wifi_enable[ip] == False and SOCKET_IO_SERVER_HOST not in url:
                flow.response = http.Response.make(500, json.dumps({'success': False, 'message': 'Wifi is disabled'}), {"Content-Type": "application/json"})
            else:                
                if not ip in self.testbed_registation or not 'redirection' in self.testbed_registation[ip] or not self.testbed_registation[ip]['redirection']:
                    flow.resume()
                    return
                if url in self.testbed_registation[ip]['redirection']:
                    binary_path = self.testbed_registation[ip]['redirection'][url]
                    if os.path.exists(binary_path):
                        flow.response = self.forge_redirect_response(binary_path, None)
                    else :
                        flow.response = http.Response.make(200, json.dumps({'success': False, 'message': 'File not found'}, {"Content-Type": "application/json"}))    
        
        flow.resume()
        return

    def response(self, flow):
        if flow.request.host in self.except_domain or flow.request.host == DEVICE_REGISTER_HOST:
            flow.resume()
            return
        
        flow.intercept()
        try:
            self.log_traffic(flow)
        except Exception as e:
            print("receive Exception of log traffic: ")
            print(e)
            
            raise e
        flow.resume()
        
    
    def log_traffic(self, flow):
        print('log traffic, request host: %s' % flow.request.host) 
        url = flow.request.url
        
        #
        url_sha256_hash = hashlib.sha256(url.encode('utf-8')).hexdigest()
        
        ip = flow.client_conn.peername[0]
        if flow.request.host == SOCKET_IO_SERVER_HOST or (ip in self.wifi_enable and self.wifi_enable[ip] == False):
            return 
        
        if ip in self.testbed_registation and 'sample' in self.testbed_registation[ip] and self.testbed_registation[ip]['sample']:
            result_folder_path = os.path.join(TRAFFIC_LOG_FOLDER, self.testbed_registation[ip]['sample'])
        else:
            result_folder_path = os.path.join(TRAFFIC_LOG_FOLDER, 'unknown')
        
    
        if not os.path.isdir(result_folder_path):
            print('should mkdir for result folder')
            os.makedirs(result_folder_path)
        else:
            print('should ndot make dir for result folder, ' + result_folder_path)
        binary_folder = os.path.join(result_folder_path, 'binary')
        if not os.path.isdir(binary_folder):
            os.makedirs(binary_folder)
        
        # save response data content to file
        if flow.response.data.content:
            content_hash = hashlib.sha256(flow.response.data.content).hexdigest()
        else:
            content_hash = None
                    
        response_info = {'url': url, 'url_hash': url_sha256_hash, 'content_hash': content_hash, 'status_code': flow.response.status_code, 'headers': dict(flow.response.headers)}
        if flow.response and flow.response.headers and 'Content-Type' in flow.response.headers:
            response_info['content_type'] = flow.response.headers['Content-Type']
        
        if content_hash is not None:
            content_path = os.path.join(binary_folder, content_hash)
            with open(content_path, 'wb') as f:
                f.write(flow.response.data.content)
                print("content path " + content_path)
            size = sys.getsizeof(flow.response.data.content)  
            detected_type = self.get_file_type(content_path)
            response_info['size'] = size
            response_info['detected_type'] = detected_type
        
        print(response_info)

        log_file_path = os.path.join(result_folder_path, url_sha256_hash + '.json')
        with open(log_file_path, 'w') as f:
            json.dump(response_info, f)
    
    def get_file_type(self, file_path): #()
        # run file-type command
        if not file_path or not os.path.isfile(file_path):
            return None
    
        ps = subprocess.Popen('file -b ' + file_path, stdout=subprocess.PIPE, shell=True)
        try:
            output, err = ps.communicate(timeout=10)
            lines = output.decode('utf-8').splitlines()
            if len(lines) > 0:
                return lines[0]
            else:
                return None    
        except subprocess.TimeoutExpired:
            ps.kill()
            return 'unknown'
        
        
        
    
    def forge_redirect_response(self, binary_file_path, header):
        if(os.path.exists(binary_file_path)):
            with open(binary_file_path, 'rb') as f:
                content = f.read()
            return http.Response.make(200, content, header if header is not None else {"Content-Type": "application/octet-stream"})
        else:
            return http.Response.make(200, json.dumps({'success': False, 'message': 'Invalid Parameter'}, {"Content-Type": "application/json"}))
    
    def load_redirection_table_from_sample_hash(self, sample_hash):
        valid_result_folder_path = os.path.join(DYNAMIC_DCL_VALID_RESULT_FOLDER_PATH, sample_hash)
        if not os.path.isdir(valid_result_folder_path): return {}
        routine_file_path = os.path.join(valid_result_folder_path,  sample_hash + '_dcl_routines.json')
        if not os.path.isfile(routine_file_path): return {}
        redirections = {}
        try:
            with open(routine_file_path, 'r') as f:
                routines = json.load(f)
            assert isinstance(routines, dict)
            for file_name in routines:
                try:
                    assert isinstance(routines[file_name], dict)
                    if 'dex' not in routines[file_name]: continue
                    if 'urls' in routines[file_name]:
                        for url in routines[file_name]['urls']:
                            if url not in redirections:
                                redirections[url] = os.path.join(valid_result_folder_path, routines[file_name]['dex'])
                except:
                    continue
        except:
            return redirections
        
        print('load redirections for {}: {}'.format(sample_hash, len(redirections)))

        return redirections

        # TODO: load from validation results of routine, and match url -> dex file
    
addons = [cyfiMitm()]


