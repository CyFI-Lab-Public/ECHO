import subprocess
import os, sys, time



PROJ_ROOT_FOLDER = os.environ[' PROJ_PATH']
if not PROJ_ROOT_FOLDER in sys.path:
    sys.path.append(PROJ_ROOT_FOLDER)

from PipelineExecutionFramework import Client, MalwareCheckConsumerWrapper
from ENV import CLASS_SCAN_RESULT_FOLDER_PATH, CLASS_SCAN_LOG_PATH, FLOWDROID_JAR_PATH, ANDROID_PLATFORM_PATH
from GleanMongoDB import MalwareManager

TOPIC = "FD_CLASS_SCAN"

class CustomConsumeWrapper(MalwareCheckConsumerWrapper):
    def __init__(self, topic, bootstrap_servers=["localhost:9092"]) -> None:
        super().__init__(topic, bootstrap_servers)
    
    def get_flowdroid_log_class_command(self, sample_path, output_file_path) -> str:
        return "java -jar {jar_path} -a {sample_path} -p {platform_path} --mergedexfiles -logclz -logclzpath {output_file_path} -na".format(jar_path=FLOWDROID_JAR_PATH, sample_path=sample_path,
        platform_path = ANDROID_PLATFORM_PATH, output_file_path=output_file_path)
    
    def init_after_pickle(self):
        return super().init_after_pickle()
        
    # The Job should always be a dict for Malware Objeect
    def pre_process(self, job): 
        super().pre_process(job)
        print(self.malware.sample_hash)
        # nothing a lot to do here, just ckeck if the file exists
        if not os.path.exists(self.malware.binary_path):
            raise Exception("Binary path not found")
    
        if self.malware.init_classes_scanned:
            raise Exception("Class scan already done")
        return None
    
    def process(self, job):
        super().process(job)
        result_path = os.path.join(CLASS_SCAN_RESULT_FOLDER_PATH, self.malware.sample_hash + '.json')
        log_file_path = os.path.join(CLASS_SCAN_LOG_PATH, self.malware.sample_hash  + ".log")
        cmd = self.get_flowdroid_log_class_command(self.malware.binary_path, result_path)
        try:
            with open(log_file_path, 'w') as f:
                p = subprocess.Popen(cmd, shell=True, stdout=f, stderr=f)
                p.communicate(timeout=60 * 10)
        except subprocess.TimeoutExpired:
            p.kill()
            p.communicate()
            print("Timeout for FlowDroid Class Scan")
            os.system("echo \"Timeout for FlowDroid Class Scan\" >> " + log_file_path) 
        except Exception:
            os.system('echo \"Exception for FlowDroid Class Scan\" >> ' + log_file_path)

        self.malware.init_classes_scanned = True
        if os.path.isfile(log_file_path):
            self.malware.class_scan_log_path = log_file_path
        if os.path.isfile(result_path):
            self.malware.class_scan_result_path = result_path
    

    def post_process(self, job):
        super().post_process(job)
        # print('post process')
        # return 
        # try:
            # self.malware_manager.update_malware(self.malware)
            # print("Finish flowdroid for {}".format(self.malware.sample_hash))
        # except:
            # pass
        # return True
    
    def handle_failure(self,exception=None):
        super().handle_failure(exception)
        if self.malware.init_classes_scanned == False:
            self.malware.init_classes_scanned = True
        

class FlowDroidClassScanPipeline(Client):
    def __init__(self, consumerWrappers: list=[], local_port: int = 9092, topic: str = TOPIC, port_forward=True, remote_config_name="raven", remote_port=9092) -> None:
        super().__init__(consumerWrappers, local_port, topic, port_forward, remote_config_name, remote_port, multi_process=True)
        self.malware_manager = MalwareManager()

    
    def reset_malware_flowdroid(self, malware_to_reset_query):
        self.malware_manager.query_and_update(malware_to_reset_query, {"init_classes_scanned": False, "class_scan_log_path": None, "class_scan_result_path": None, "idle": True })
    
    
    @staticmethod
    def start_pipe(num_of_consumers=10): 
        consumerWrappers =  [CustomConsumeWrapper(topic=TOPIC) for i in range(num_of_consumers)]
        client = FlowDroidClassScanPipeline(consumerWrappers, port_forward=False)
        ##### this api is DANGEROUS!! #### 
        ##### It will reset all malware in DB for flowDroid Class Scan ####
        # client.reset_malware_flowdroid({"init_classes_scanned": True})
        client.clear_job_queue()
        client.start_consumer_processes()

        while(True):
            jobs = client.malware_manager.get_flowdroid_package_scan_list()
            # print("Got {} jobs".format(len(jobs)))
            if len(jobs) > 0:
                client.produce_jobs(jobs)
            time.sleep(60 * 10)
            
            
if __name__ == '__main__':
    FlowDroidClassScanPipeline.start_pipe(10)
