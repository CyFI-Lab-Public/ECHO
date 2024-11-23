# -*- coding:utf-8 -*-
# coding=utf-8
from typing import Callable
import frida
import sys, os
import json
import time


PROJ_ROOT_FOLDER = os.environ[' PROJ_PATH']
if not PROJ_ROOT_FOLDER in sys.path:
    sys.path.append(PROJ_ROOT_FOLDER)


from ENV import UNPACK_PAYLOAD_PATH 

class FridaUnpacker:
    
    def __init__(self, package_name, device_name, result_folder_path = "", payload_path="",on_message:Callable=None):
        self.package_name = package_name
        self.device_name = device_name
        
        self.result_folder_path = result_folder_path
        self.payload_path = payload_path
        
        # self.dex_paths = []
        # self.bridges = set()
        self.on_message_callback = on_message
        self.interrupt = False        
        self.interrupt_erorr = ""
        
    def on_message(self, message, data):
        # return
        # print("Message: ==========")
        print(message)
        return
        ##### Uncomment below as a on_message template #####
     
        # if message['type'] == 'send':
        #     payload = message['payload']
        #     if 'type' in payload and  payload['type'] == 'dex_file' and payload['dex_type'].startswith('dex') and data != None:
        #         print('dex received: %s' % payload['file_name'])
        #         with open(payload['file_name'], 'wb') as f:
        #             f.write(data)
        #         self.dex_paths.append(payload['file_name'])
        #     if 'type' in payload and  payload['type'] == 'bridge':
        #         try:
        #             self.bridges.add(payload['bridge'])
        #             with open(self.bridge_result_file_path, 'w') as f:
        #                 json.dump(list(self.bridges), f)
        #         except Exception as e:
        #             print(e)
        #             return
        # elif message['type'] == 'error':
        #     print(message)


    # 9.0 arm 需要拦截　_ZN3art13DexFileLoader10OpenCommonEPKhjS2_jRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_NS3_10unique_ptrINS_16DexFileContainerENS3_14default_deleteISH_EEEEPNS0_12VerifyResultE
    # 7.0 arm：_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_

    # android 10: libdexfile.so 
    # #_ZN3art13DexFileLoader10OpenCommonEPKhjS2_jRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_NS3_10unique_ptrINS_16DexFileContainerENS3_14default_deleteISH_EEEEPNS0_12VerifyResultE
    def get_payload(self,  package_name):
        # '/Users/zhangrunze/workplace/ProjDropperChain/DynamicProcessing/Unpacker/fridaPayloadUnpack.js'
        if(not os.path.exists(self.payload_path)):
            return None

        with open(self.payload_path, 'r') as f:
            template = str(f.read())
        template =  self.template_after_format(template, package_name=package_name)
        with open('payload_%s.js' % self.device_name, 'w') as f:
            f.write(template)
        os.system('frida-compile -o payload_compile_%s.js payload_%s.js' % (self.device_name, self.device_name))
        with open('payload_compile_%s.js' % self.device_name, 'r') as f:
            src = str(f.read())
        # os.remove('payload_compile_%s.js' % self.device_name)
        os.remove('payload_%s.js' % self.device_name)
        return src

    def template_after_format(self, template, **kwargs):
        for key, value in kwargs.items():
            template = template.replace("#%s#" % key, value)
        return template


    def frida_inject_unpack_code_and_start_app(self, package_name, deviceId, timeout = 30):
        # package = sys.argv[1]
        # print("dex output to: /data/data/%s" % (package))        
        # self.on_message_callback("test", "test")
        try: 
            device = frida.get_device(deviceId)
        except:
            print("ERROR: device not found")
            return
                
        try:
            p = device.get_process(package_name)
            device.kill(p.pid)
        except:
            pass
        
        src = self.get_payload(package_name)
        if not src:
            return 
        
        pid = device.spawn(package_name)
        session = device.attach(pid)

        script = session.create_script(src)
        script.on("message", self.on_message_callback if self.on_message_callback is not None else self.on_message)        
        script.load()
        device.resume(pid)
        current = timeout
        while current >= 0:
            time.sleep(1)
            if self.interrupt:
                self.interrupt = False
                raise Exception(self.interrupt_erorr)
            current -= 1            
            if current == 0:
                break

        # sys.stdin.read()
        
        
    def start_unpack(self, timeout=10):
        # self.dex_paths.clear()
        # self.bridges.clear()
        # pre processing before unpack
        self.frida_inject_unpack_code_and_start_app(self.package_name, self.device_name, timeout)        
        
        # post processing after unpack
    
        
    
if __name__ == '__main__':
    package_name = "com.global.bricksball.mt"
    device = '8C3X1HKWU'
    
    fridaUnpacker = FridaUnpacker(package_name, device, payload_path=UNPACK_PAYLOAD_PATH)
    fridaUnpacker.start_unpack(timeout=30)
