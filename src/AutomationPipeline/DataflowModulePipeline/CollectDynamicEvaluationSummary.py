import os, json, sys 

PROJ_ROOT_FOLDER = os.environ['GLEAN_PATH']
if not PROJ_ROOT_FOLDER in sys.path:
    sys.path.append(PROJ_ROOT_FOLDER)

from Utility import Utility
from GleanMongoDB import Malware, MalwareManager

from ENV import DYNAMIC_EVALUATION_SUMMARY_FOLDER_PATH


if __name__ == '__main__':
    
    malware_manager = MalwareManager()
    malware_dicts = malware_manager.get_samples_by_query({'dynamic_dcl_processed': True, 'is_candidate': True, 'unpack_success':True, 'is_jsi_possible': True})
    valid_count = 0
    for malware_dict in malware_dicts:
        malware = Malware(malware_dict)
        print(malware.sample_hash)
        
        interfaceNameSet = set()
        try:
            with open(malware.dynamic_dcl_result_path , 'r') as f:
                dcl_results = json.load(f)
            for dcl_result in dcl_results:
                if 'type' not in dcl_result:
                    continue
                if dcl_result['type'] == 'webview' and 'interfaceNames' in dcl_result:
                    interfacesNames = [i for i in dcl_result['interfaceNames'] if not i.startswith('googleAdsJsInterface:com.google.android.gms.')]
                    interfaceNameSet.update(interfacesNames)
        except:
            print('dynamic dcl result not found')
            continue
        
        if len(interfaceNameSet) == 0:
            malware.is_jsi_possible = False
        else:
            malware.is_jsi_possible = True
            valid_count += 1
        malware.is_candidate = malware.is_dcl_possible or malware.is_jsi_possible
        malware_manager.update_malware(malware)
        print('done: ', malware.sample_hash)
        
    print('jsi still valid: {} of {}'.format(valid_count, len(malware_dicts)))
# 
