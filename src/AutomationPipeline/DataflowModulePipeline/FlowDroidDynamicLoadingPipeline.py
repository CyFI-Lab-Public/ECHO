from genericpath import isfile
import subprocess
import os, sys, json
import time

PROJ_ROOT_FOLDER = os.environ[' PROJ_PATH']
if not PROJ_ROOT_FOLDER in sys.path:
    sys.path.append(PROJ_ROOT_FOLDER)

from Utility import Utility

from PipelineExecutionFramework import Client, MalwareCheckConsumerWrapper
from ENV import FLOWDROID_DCL_LOG_FOLDER_PATH, FLOWDROID_DCL_RESULT_FOLDER_PATH, FLOWDROID_DCL_SINK_FILE_PATH, KAFKA_LOCAL_PORT,FLOWDROID_DCL_SINK_FILE_PREFIX,KAFKA_PORT, FLOWDROID_JAR_PATH, ANDROID_PLATFORM_PATH, FlowDROID_DCL_SINK_FILE_TYPES
from GleanMongoDB import MalwareManager, Malware


TOPIC = "FD_DCL"

class CustomConsumeWrapper(MalwareCheckConsumerWrapper):
    def __init__(self, topic, bootstrap_servers=["localhost:9092"]) -> None:
        super().__init__(topic, bootstrap_servers)

    def get_flowdroid_dcl_command(self, sample_path, output_file_path, source_sink_path, bridge_path) -> str:
        cmd =  "java -jar {jar_path} -a {sample_path} -p {platform_path}  -s {source_sink_path} -o {output_file_path} -of json  --mergedexfiles -dl -ot --paths -logcall".format(jar_path=FLOWDROID_JAR_PATH, 
                                                        sample_path=sample_path, 
                                                        platform_path=ANDROID_PLATFORM_PATH,
                                                        output_file_path=output_file_path,
                                                        source_sink_path=source_sink_path,
                                                        bridge_path=bridge_path)

        
        if os.path.exists(bridge_path):
            cmd += " --extraentrypoint {bridge_path}".format(bridge_path=bridge_path)
        return cmd

    # The Job should always be a dict for Malware Objeect
    def pre_process(self, job):
        super().pre_process(job)

        # nothing a lot to do here, just ckeck if the file exists
        if not os.path.exists(self.malware.binary_path):
            raise Exception("Binary path not found")
    
        if self.malware.flowdroid_dcl_processed == True:
            raise Exception("FD DCL already done")
        
        if not self.malware.unpack_sample_binary_path or not os.path.exists(self.malware.unpack_sample_binary_path):
            raise Exception("Unpacked path not found")
        
        self.num_of_dcl_routine = 0
        
        return None
    
    def process(self, job):
        super().process(job)
        result_folder_path = os.path.join(FLOWDROID_DCL_RESULT_FOLDER_PATH, self.malware.sample_hash)
        if not os.path.exists(result_folder_path):
            os.mkdir(result_folder_path)    
            
            
        for sink_type in FlowDROID_DCL_SINK_FILE_TYPES:
            sink_source_file_path = os.path.join(FLOWDROID_DCL_SINK_FILE_PATH, FLOWDROID_DCL_SINK_FILE_PREFIX + sink_type + ".txt")
            result_path = os.path.join(result_folder_path, self.malware.sample_hash + "_" + sink_type + ".json")
            self.run_flowdroid_with_sink_file(sink_source_file_path, result_path, sink_type)
            
        if os.path.isdir(result_folder_path):
            self.malware.flowdroid_dcl_result_path = result_folder_path
        
        try:
            with open(result_path, 'r') as f:
                dcl_result = json.load(f)         
                self.malware.num_of_dcl_routine = len(dcl_result)
        except:
            pass
        

    def post_process(self, job):
        super().post_process(job)
        self.malware.flowdroid_dcl_processed = True
        self.update_if_malware_is_ready_for_validation()
    
    
    def update_if_malware_is_ready_for_validation(self):
        if self.malware is None:
            return 
        
        dcl_ready = self.malware.is_dcl_possible and self.flowdroid_dcl_processed or not self.malware.is_dcl_possible
        jsi_ready = self.malware.is_jsi_possible and self.flowdroid_jsi_processed or not self.malware.is_jsi_possible
        self.malware.ready_for_validation = self.malware.is_candidate and dcl_ready and jsi_ready
        
        
        
    
    def handle_failure(self, exception=None):
        super().handle_failure(exception)
        if self.malware.flowdroid_dcl_processed == False:
            self.malware.flowdroid_dcl_processed = True
        
    def run_flowdroid_with_sink_file(self, sink_file_path, result_file_path, sink_type):
        log_file_path = os.path.join(FLOWDROID_DCL_LOG_FOLDER_PATH, self.malware.sample_hash  + ".log")

        cmd = self.get_flowdroid_dcl_command(self.malware.unpack_sample_binary_path, result_file_path, sink_file_path, self.malware.unpack_bridge_path)
        try:
            with open(log_file_path, 'w+') as f:
                p = subprocess.Popen(cmd, shell=True, stdout=f, stderr=f)
                p.communicate(timeout=15 * 60)
        except subprocess.TimeoutExpired:
            p.kill()
            p.communicate()
            print("Timeout for FlowDroid DCL" + sink_type)
            os.system("echo 'Timeout for FlowDroid DCL' {}>> ".format(sink_type) + log_file_path) 
        except Exception as e:
            os.system('echo "Exception for FlowDroid DCL {}" >> '.format(sink_type) + log_file_path)

        if not self.malware.flowdroid_dcl_log_path and  os.path.isfile(log_file_path):
            self.malware.flowdroid_dcl_log_path = log_file_path

        # if os.path.isfile(result_file_path):
        #     try:
        #         # with open(result_file_path, 'r') as f:
        #         #     dcl_result = json.load(f)         
        #         #     self.malware.num_of_dcl_routine += len(dcl_result)
        #     except:
        #         pass
        
            
            
class FlowDroidDCLPipelineClient(Client):
    def __init__(self, consumerWrappers: list=[], local_port: int = 9092, topic: str = TOPIC, port_forward=True, remote_config_name="", remote_port=9092, multi_process=True) -> None:
        super().__init__(consumerWrappers, local_port, topic, port_forward, remote_config_name, remote_port, multi_process=multi_process)
        self.malware_manager = MalwareManager()
    
    @staticmethod
    def start_pipe(num_of_consumers=6): 
        consumerWrappers =  [CustomConsumeWrapper(topic=TOPIC) for i in range(num_of_consumers)]
        client = FlowDroidDCLPipelineClient(consumerWrappers, local_port=KAFKA_LOCAL_PORT, topic=TOPIC, port_forward=True, remote_config_name="raven", remote_port=KAFKA_PORT, multi_process=True)
        
        client.clear_job_queue()
        # client.malware_manager.query_and_update({'flowdroid_dcl_processed': True}, {'flowdroid_dcl_processed': False, 'flowdroid_dcl_result_path': "", "num_of_dcl_routine": 0}) 
        # return

        client.start_consumer_processes()
        
        while(True):   
            jobs = client.malware_manager.get_flowdroid_dcl_list()
            if len(jobs) > 0:
                client.produce_jobs(jobs)
            time.sleep(60 * 10)
            pass
        
if __name__ == '__main__':
    FlowDroidDCLPipelineClient.start_pipe(6)
