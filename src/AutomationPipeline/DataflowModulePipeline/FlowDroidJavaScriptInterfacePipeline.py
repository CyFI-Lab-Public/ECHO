import subprocess
import os, sys
import json, time 

PROJ_ROOT_FOLDER = os.environ[' PROJ_PATH']
if not PROJ_ROOT_FOLDER in sys.path:
    sys.path.append(PROJ_ROOT_FOLDER)

from PipelineExecutionFramework import Client, MalwareCheckConsumerWrapper
from ENV import FLOWDROID_JAR_PATH, ANDROID_PLATFORM_PATH, FLOWDROID_JSI_LOG_FOLDER_PATH, FLOWDROID_JSI_RESULT_FOLDER_PATH, FLOWDROID_JSI_SINK_FILE_PATH, KAFKA_LOCAL_PORT, KAFKA_PORT
from GleanMongoDB import MalwareManager, Malware
from Utility import Utility

TOPIC = "FD_JSI"

sink_method_to_category = {}
sink_category_to_methods = {}
class CustomConsumeWrapper(MalwareCheckConsumerWrapper):
    def __init__(self, topic, bootstrap_servers=["localhost:9092"]) -> None:
        super().__init__(topic, bootstrap_servers)

    def get_flowdroid_jsi_command(self, sample_path, output_file_path, source_sink_path, jsi_interface_of_interest_path) -> str:
        return "java -jar {jar_path} -a {sample_path} -p {platform_path} --mergedexfiles -s {source_sink_path} -o {output_file_path} -of json -js -ot -logcall --jsiinterfaceofinterest {jsi_interface_of_interest_path}".format(jar_path=FLOWDROID_JAR_PATH, sample_path=sample_path, platform_path = ANDROID_PLATFORM_PATH, output_file_path=output_file_path,
        source_sink_path=source_sink_path, jsi_interface_of_interest_path=jsi_interface_of_interest_path)
    
    # The Job should always be a dict for Malware Objeect
    def pre_process(self, job):
        super().pre_process(job)
        print(self.malware.sample_hash)
        # nothing a lot to do here, just ckeck if the file exists

        if not os.path.exists(self.malware.binary_path):
            raise Exception("Binary path not found")
    
        if self.malware.flowdroid_jsi_processed == True:
            raise Exception("FD JSI already done")
        
        if not self.malware.unpack_sample_binary_path or not os.path.exists(self.malware.unpack_sample_binary_path):
            raise Exception("Unpacked path not found")
        
        if not Utility.is_valid_jsi_interface_of_interest_list_file_path(self.malware.dynamic_dcl_jsi_of_interest_path):
            raise Exception('Invalid JSI of interest list file path')
        
        return None
    
    def process(self, job):
        super().process(job)
        
        result_path = os.path.join(FLOWDROID_JSI_RESULT_FOLDER_PATH, self.malware.sample_hash + '.json')
        log_file_path = os.path.join(FLOWDROID_JSI_LOG_FOLDER_PATH, self.malware.sample_hash  + ".log")
        cmd = self.get_flowdroid_jsi_command(self.malware.binary_path, result_path, FLOWDROID_JSI_SINK_FILE_PATH, self.malware.dynamic_dcl_jsi_of_interest_path)
        try:
            with open(log_file_path, 'w') as f:
                p = subprocess.Popen(cmd, shell=True, stdout=f, stderr=f)
                p.communicate(timeout=60 * 60)
        except subprocess.TimeoutExpired:
            p.kill()
            p.communicate()
            print("Timeout for FlowDroid JSI")
            os.system("echo \'Timeout for FlowDroid JSI\' >> " + log_file_path) 
        except Exception:
            os.system('echo \"Exception for FlowDroid JSI\" >> ' + log_file_path)

        if os.path.isfile(log_file_path):
            self.malware.flowdroid_jsi_log_path = log_file_path
        if os.path.isfile(result_path):
            self.malware.flowdroid_jsi_result_path = result_path
            try:
                with open(result_path, 'r') as f:
                    jsi_result = json.load(f)         
                    self.malware.num_of_jsi_interface_entrypoint = len(jsi_result)
            except:
                pass

    def post_process(self, job):
        super().post_process(job)
        self.malware.flowdroid_jsi_processed = True
        self.update_if_malware_is_ready_for_validation()
    
    def update_if_malware_is_ready_for_validation(self):
        if self.malware is None:
            return     
        dcl_ready = self.malware.is_dcl_possible and self.malware.flowdroid_dcl_processed or not self.malware.is_dcl_possible
        jsi_ready = self.malware.is_jsi_possible and self.malware.flowdroid_jsi_processed or not self.malware.is_jsi_possible
        self.malware.ready_for_validation = self.malware.is_candidate and dcl_ready and jsi_ready
        self.malware.flowdroid_jsi_entrypoint_with_takedown_capability = len(CustomConsumeWrapper.find_validate_entrypoint_with_jsi_capability(self.malware))
        
    
    def handle_failure(self, exception=None):
        super().handle_failure(exception)
        if self.malware.flowdroid_jsi_processed == False:
            self.malware.flowdroid_jsi_processed = True

    @staticmethod
    def find_validate_entrypoint_with_jsi_capability(malware:Malware) -> dict:
        fd_result_path = malware.flowdroid_jsi_result_path
        res = {}
        if not fd_result_path:
            return res
        if not os.path.isfile(fd_result_path):
            return res
        with open(fd_result_path, 'r') as f:
            fd_jsi_result = json.load(f)
        if not isinstance(fd_jsi_result, dict):
            return res
    
        for ep_sig in fd_jsi_result.keys():
            ep_body = fd_jsi_result[ep_sig]
            if not isinstance(ep_body, dict) or 'sinkMethods' not in ep_body:
                continue
            sink_methods_dict = ep_body.get('sinkMethods', [])
            if not sink_methods_dict or len(sink_methods_dict) == 0:
                continue
            
            is_ep_with_capability = False
            key_to_pop = []
            for sink_method_sig in sink_methods_dict.keys():
                # if  'exit' in sink_method_sig or 'finish' in sink_method_sig:
                #     print('is exit')
                if CustomConsumeWrapper.get_category_for_sink_method(sink_method_sig) == 'Stop':
                    # print('find a stop api: ' + sink_method_sig)
                    is_ep_with_capability = True
                    continue
                #     print('find an exit: ' + sink_method_sig)
                
                sink_method_body = sink_methods_dict[sink_method_sig]
                args = sink_method_body.get('args', [])
                sink_method_has_tainted_arg = False
                for arg_body in args:
                    if 'argSources' in arg_body and len(arg_body['argSources']) > 0:
                        sink_method_has_tainted_arg = True
                if sink_method_has_tainted_arg:
                    is_ep_with_capability = True
                else:
                    key_to_pop.append(sink_method_sig)
            
            for sink_method_sig in key_to_pop:
                sink_methods_dict.pop(sink_method_sig)
            
            if is_ep_with_capability:
                res = {ep_sig: ep_body}
        return res
    
    @staticmethod
    def init_sink_method_category_dicts():
        if len(sink_category_to_methods) > 0 and len(sink_method_to_category) > 0:
            return
        with open(FLOWDROID_JSI_SINK_FILE_PATH, 'r') as f:
            lines = f.readlines()
        current_category = None
        
        for line in lines:
            if len(line.strip()) == 0:
                continue
            if line.startswith('#'):
                current_category = line.strip()[1:]
                if current_category not in sink_category_to_methods:
                    sink_category_to_methods[current_category] = set()
                continue
            else:
                method_sig = line.split('->')[0].strip()
                sink_category_to_methods[current_category].add(method_sig)
                sink_method_to_category[method_sig] = current_category
    
        return None
    
    @staticmethod
    def get_category_for_sink_method(method_sig):
        CustomConsumeWrapper.init_sink_method_category_dicts()
        return sink_method_to_category.get(method_sig, None)
        
        
    @staticmethod
    def get_sink_methods_by_category(category):
        CustomConsumeWrapper.init_sink_method_category_dicts()
        return list(sink_category_to_methods.get(category, set()))
        
        
        
    
    # def finish(self):
    #     ## not update for current test 
    #     pass

class FlowDroidJSIPipelineClient(Client):
    def __init__(self, consumerWrappers: list=[], local_port: int = KAFKA_LOCAL_PORT, topic: str = TOPIC, port_forward=True, remote_config_name="raven", remote_port=KAFKA_PORT, multi_process=True) -> None:
        super().__init__(consumerWrappers, local_port, topic, port_forward, remote_config_name, remote_port,multi_process)
        self.malware_manager = MalwareManager()
    
    @staticmethod
    def start_pipe(num_of_consumers=10): 
        consumerWrappers =  [CustomConsumeWrapper(topic=TOPIC) for i in range(num_of_consumers)]
        # client = FlowDroidJSIPipelineClient(consumerWrappers, port_forward=True)
        client = FlowDroidJSIPipelineClient(consumerWrappers, local_port=KAFKA_LOCAL_PORT, topic=TOPIC, port_forward=True, remote_config_name="raven", remote_port=KAFKA_PORT, multi_process=True)
        client.malware_manager.query_and_update({'is_jsi_possible': True, 'unpack_success': True, 'idle': False, 'flowdroid_jsi_processed': False}, {'idle': True})
        # client.malware_manager.query_and_update({'flowdroid_jsi_processed': True}, {'flowdroid_jsi_processed': False})
        # exit()

        client.clear_job_queue()
        client.start_consumer_processes()
        
        while(True):
            jobs = client.malware_manager.get_flowdroid_jsi_list()
            print("Got {} jobs".format(len(jobs)))
            if len(jobs) > 0:
                client.produce_jobs(jobs)
            time.sleep(60 * 10)
            
if __name__ == '__main__':
    FlowDroidJSIPipelineClient.start_pipe(4)