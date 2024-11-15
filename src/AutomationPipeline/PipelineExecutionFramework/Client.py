import os, sys, time
import json
from multiprocessing import Process
from threading import Thread
import threading
import multiprocessing
from kafka import KafkaConsumer, KafkaProducer
from bson import json_util


PROJ_ROOT_FOLDER = os.environ['GLEAN_PATH']
if not PROJ_ROOT_FOLDER in sys.path:
    sys.path.append(PROJ_ROOT_FOLDER)

from ENV import KAFKA_HOST_IP_ADDR, KAFKA_LOCAL_PORT, KAFKA_PORT
from Utility import Utility


## Usage: if connection not working, and port is forwarded, try to add your host to /etc/hosts as a localhost, like: 
#127.0.0.1   abhishek-Standard-PC-i440FX-PIIX-1996
# the host name can either be get from ssh connection or by using kafka quickstart and check the error message
# check this link for more info:
# https://kafka.apache.org/quickstart
# follow step 3 if you have already have your broker server running and topic setup



class JobConsumer(KafkaConsumer):
    def __init__(self,topic,bootstrap_servers=['localhost:9092']) -> None: 
        # args and kwargs are static arguments passed to run_job
        super().__init__(topic,group_id = topic, bootstrap_servers=bootstrap_servers,max_poll_records=1, enable_auto_commit=True, max_poll_interval_ms=1000 * 60 * 60 * 12)
        if not self.bootstrap_connected():
            raise Exception("Consumer not connect to Kafka")
        
        # self.count = 0
    
    def consume_jobs(self, call_back_func):
        # do whatever you want with the job       
        print('start consuming on ' + multiprocessing.current_process().name)
        
        while True:   
            try:
                polls = self.poll(max_records=1)
                if(len(polls) == 0):
                    time.sleep(0.1)
                    continue
                self.commit()
                records = list(polls.values())[0]
                print('consumed ' + str(len(records)) + ' records on ' + multiprocessing.current_process().name +  " thread: " + str(threading.current_thread().name))
                for message in records:
                    if message is not None and message.value is not None:
                        job = json.loads(message.value.decode('utf-8'))
                        call_back_func(job)
            except Exception as e:
                print("Exception of consume jobs:" + str(e))
                continue
        
        print('Error: consume_jobs should not reach here')
        raise Exception("consume_jobs should not reach here")
        
    def clear_job_queue(self):
        count = 0
        cleared = 0
        while count < 10:
            polls = self.poll(max_records=1, timeout_ms=1000)
            cleared += len(polls)
            if(len(polls) == 0):
                time.sleep(0.1)
                count += 1
            self.commit()
            if cleared % 1000 == 0:
                print('cleared: ' + str(cleared) + ' jobs')

        print('cleared job queue {} samples'.format(cleared))
        

class ConsumerWrapper:
    # should have everything good for run job. but init kafkaconsumer only when consume_jobs is called, do not do that in init, which is beore pickle is called
    def __init__(self, topic, bootstrap_servers=['localhost:9092'], thread_name=None) -> None:
        self.consumer = None
        self.topic = topic
        self.bootstrap_servers = bootstrap_servers
        self.thread_name = thread_name
    
    def init_after_pickle(self) -> bool:
        if self.consumer == None:
            self.consumer = JobConsumer(topic=self.topic, bootstrap_servers=self.bootstrap_servers)
    
        return True
    
    def consume_jobs(self) -> None:
        if self.init_after_pickle():
            self.consumer.consume_jobs(self.run_job)
        else:
            raise RuntimeError("ConsumerWrapper initialization failed")
        
    def pre_process(self, job, malware=None) -> bool:
        raise NotImplementedError

    def process(self, job, malware=None):
        raise NotImplementedError
    
    def post_process(self, job, malware=None) -> bool:
        raise NotImplementedError

    def handle_failure(self, job, malware=None):
        raise NotImplementedError
    
    def run_job(self, job):
        print(str(job) + " on " + multiprocessing.current_process().name + " thread: " + str(threading.current_thread().name))
        try:
            pre_result_malware = self.pre_process(job)
            result_malware = self.process(job, pre_result_malware)
            self.post_process(job, result_malware)
        except Exception:
            self.handle_failure(job)
            # return True    
        return True
        

        
class JobProducer(KafkaProducer):
    def __init__(self, bootstrap_servers=['localhost:9092']) -> None:
        super().__init__(bootstrap_servers=bootstrap_servers)
        if(not self.bootstrap_connected()):
            raise Exception("Producer failed to connect to Kafka")        


    def produce_jobs(self, topic, jobs:list):
        for job in jobs:
            if isinstance(job, dict):
                try:
                    job_str = json.dumps(job)
                except TypeError:
                    job_str = json_util.dumps(job)
                    
            elif isinstance(job, str):
                job_str = job
            else:
                raise Exception("Job is not a dict")
            self.send(topic, job_str.encode('utf-8'))    
        self.flush()
        

class Client:
    def __init__(self, consumerWrappers:list=[], local_port:int=KAFKA_LOCAL_PORT, topic:str="default",port_forward=True,remote_config_name="raven",remote_port=KAFKA_PORT, multi_process=True) -> None:
        self.port = local_port 
        if port_forward:
            if not Utility.get_my_ip() == KAFKA_HOST_IP_ADDR:
                if local_port == 0 or remote_config_name == "" or remote_port == 0:
                    raise Exception("Port forwarding is enabled, but local_port, remote_config_name, remote_port are not set")
                self.setup_port_forwarding(local_port, remote_config_name, remote_port)
                self.port = local_port if (port_forward and local_port != 0) else remote_port

        self.topic = topic
        self.cleaner = None
        self.producer = JobProducer()
        self.consumerWrappers = consumerWrappers
        
        self.multi_process = multi_process
        self.consumer_processes = []
        self.consumer_threads = []
        self.job_in_queue = []
        

    def close(self):
        self.producer.close()

    def clear_job_queue(self):
        if self.cleaner == None:
            self.cleaner = JobConsumer(topic=self.topic)
        
        self.cleaner.clear_job_queue()
        # self.cleaner.close()
        self.cleaner = None
        
    
    def set_consumer_wrappers(self, consumerWrappers:list):
        self.consumerWrappers = consumerWrappers
    
        
        
    def setup_port_forwarding(self, local_port: int, remote_config_name: str, remote_port: int) -> None:
        if(not Utility.check_port_forwarding(local_port)):
            if(not Utility.setup_port_forwarding(local_port, remote_config_name, remote_port)):
                raise Exception("Failed to setup port forwarding for beanstalkd")

    def start_consumers(self):
        if self.multi_process:
            self.start_consumer_processes()
        else:
            self.start_consumer_threads()
    
    def start_consumer_processes(self):
        # for consumer in self.consumers:
        #     p = Process(target=self.start_consumer_processes, args=(consumer,))
        processes = [Process(target=self.start_single_consumer, args=(consumerWrapper,)) for consumerWrapper in self.consumerWrappers]
        self.consumer_processes = processes
        
        for process in processes:
            process.start()
        print('start consumer threads: ' + str(len(self.consumerWrappers)))

    def start_consumer_threads(self):
        threads = [Thread(target=self.start_single_consumer, args=(consumerWrapper,), name=consumerWrapper.thread_name) for consumerWrapper in self.consumerWrappers]
        self.consumer_threads = threads
        
        for t in threads:
            t.start() 
        print('start consumer threads: ' + str(len(self.consumerWrappers)))
    
    
        
    @staticmethod
    def start_single_consumer(consumerWrapper:ConsumerWrapper):
        consumerWrapper.consume_jobs()
            # self.beanstalk.put(job_str, ttr=3600 * 24)
            
        # print(self.beanstalk.stats_tube('test'))

    def produce_jobs(self, jobs:list):
        if self.producer is None:
            raise Exception("Producer is not initialized")
        job_to_be_produced = [job for job in jobs if isinstance(job, dict) and 'sample_hash' in job and not job['sample_hash'] in self.job_in_queue]
        self.job_in_queue.extend([job['sample_hash'] for job in job_to_be_produced])
        print("Got {} jobs".format(len(job_to_be_produced)))
        self.producer.produce_jobs(self.topic, job_to_be_produced)


def test():       
    #client = Client(topic="test",local_port=9092, port_forward=True, remote_config_name="raven", remote_port=9092)
    ## local test
    client = Client(topic="test",local_port=9092, port_forward=False, multi_process=False)
    
    consumers = [ConsumerWrapper("test"), ConsumerWrapper("test")]
    client.set_consumer_wrappers(consumers)
    client.start_consumers()
    
    time.sleep(3)
    
    jobs = [{'test': i} for i in range(100)]
    client.produce_jobs(jobs)
    while(True):
        pass


if __name__ == '__main__':   
    test()
    