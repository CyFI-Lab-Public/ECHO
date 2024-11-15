import chunk
import os, time, subprocess, queue, json
from datetime import datetime
import re, shutil
import socket
import hashlib
import ControlENV
from DynamicProcessing.ADB.ADB import ADB
from ControlENV import *



def parse_uhubctl(adbs):
    os.system("sudo usb-reset -a")
    time.sleep(5)
    # parse output from the `uhubctl` command to form a association between
    #  devices to their particular port and hub location.
    ret = {}

    devices_names = set([adb.device for adb in adbs])
    cmd = 'sudo uhubctl'
    ps = subprocess.Popen(cmd,
                          stdin=subprocess.PIPE,
                          stdout=subprocess.PIPE,
                          shell=True)
    ps.wait()
    data = ps.stdout.readlines()

    current_hub = None
    for line in data:
        l = line.decode().strip()

        if l.startswith('Current status for hub'):
            # Found hub
            current_hub = l.split(' ')[4]
            continue

        lsplit = l.split(' ')
        device = lsplit[-1].replace(']', '')
        if device in devices_names:
            ret[device] = {
                "port": lsplit[1].replace(':', ''),
                "location": current_hub
            }

    return ret


def start_proxy():
    #TODO: Write start_proxy.sh
    kill_process_with_listening_port(8080) # kill existing proxy
    proxy_cmd = 'sudo bash ./start_proxy.sh'
    try:
        pp = subprocess.Popen(proxy_cmd.split(' '))
    except Exception as ex:
        pass
    time.sleep(20)



# def get_devices(adb_path='adb'):
#     with open(os.devnull, 'wb') as devnull:
#         subprocess.check_call([adb_path, 'start-server'],
#                               stdout=devnull,
#                               stderr=devnull)
#     out = subprocess.check_output([adb_path, 'devices']).splitlines()
#     # The first line of `adb devices` just says "List of attached devices", so
#     # skip that.
#     devices = []
#     for line in out[1:]:
#         l = line.decode().strip()
#         if not l:
#             continue
#         if 'offline' in l:
#             continue
#         serial, _ = re.split(r'\s+', l, maxsplit=1)
#         if serial not in DEVICE_EXCEPT_LIST:
#             devices.append(serial)
#     return devices


def get_package_name(apk_path):
    try:
        ret = subprocess.check_output(['aapt', 'dump', 'badging',
                                    apk_path]).decode()
        assert "package: name='" in ret, "can not read package name from apkfile"
        return ret.split("package: name='")[1].split("'")[0]
    except Exception as ex:
        try:
            ret = subprocess.check_output((['aapt2', 'dump', apk_path])).decode()
            assert "Package name=" in ret, "can not read package name from apkfile"
            return ret.split('Package name=')[1].split(' ')[0]
        except:
            pass
        pass

    return None

def clear_previous_proxy_results(sample_hash):
    # /PATH/TO/BURP/reqlogs/com.foo.goo
    proxy_result_path = os.path.join(PROXY_RESULT_FOLDER, sample_hash)
    # /PATH/TO/BURP/reqlogs/no
    # no_path = os.path.join(PROXY_RESULT_FOLDER, 'no')

    try:
        shutil.rmtree(proxy_result_path)
    except FileNotFoundError:
        pass

def kill_process_with_listening_port(port):
    port = str(port)

    args = ["netstat", "-tupln"]
    output = str(subprocess.run(args, stdout=subprocess.PIPE).stdout)

    lines = output.split("\\n")
    pid = ""
    for l in lines:
        if port not in l: continue
        segs = l.split()[-1]
        pid, _ = segs.split("/")

    if pid != "":
        os.system("kill -9 %s" % pid)

def init_adb_queue():
    devices = get_devices()
    adbs = [ADB(device) for device in devices]

    hub_locations = parse_uhubctl(adbs)
    for adb in adbs:
        adb.set_hub_locations(hub_locations)
        adb.ip = adb.getip()

    q = queue.Queue(maxsize=len(adbs))
    [q.put(a) for a in adbs]

    execute_q = {}
    for a in adbs:
        execute_q[a.device] = a

    print("total devices: %d" % len(adbs))
    device_num = len(adbs)

    return q, execute_q, device_num

#
# def do_restore(adb):
#     print('Recovering %s to a previous point' % (adb.device))
#     time.sleep(2)
#     # print("Reboot to recovery")
#
#     # Reboot to recovery
#     adb.execute('reboot recovery')
#     time.sleep(20)
#
#     adb.execute('shell twrp mount system')
#
#     # Delete /sdcard stuff
#     adb.execute('shell rm -rf /sdcard/*')
#     adb.execute('shell rm -rf /sdcard/.*')
#
#     adb.execute('push %s /sdcard/' % (adb.restore_path), timeout=10000)
#
#     # Perform restoration
#     time.sleep(2)
#     # print("Restoring now")
#     adb.execute('shell twrp restore /sdcard/backup1 SDC', timeout=10000)
#
#     # reboot
#     adb.execute('reboot')
#     # Wait for system reboot
#     time.sleep(90)
#     adb.execute('shell su -c "pm disable de.robv.android.xposed.installer"')
#     print("Done restoring")

def get_sample_list():
    malware_list = os.listdir(SAMPLE_FOLDER_PATH)

    sample_list = []
    try:
        if os.path.exists('package_names.json'):
            with open('package_names.json', 'r') as f:
                package_names = json.load(f)
        else:
            package_names = {}
    except:
        package_names = {}


    # with open("/home/cyfi/workplace/ProjDropperChain/DynamicProcessing/ICS_APP/install_fail.json", 'r') as f:
    #     fail_list = json.load(f)
    fail_list = []

    for idx, malware in enumerate(malware_list):
        if malware.endswith(".bin"):
            os.system('mv {} {}'.format(os.path.join(SAMPLE_FOLDER_PATH, malware), os.path.join(SAMPLE_FOLDER_PATH, malware.replace('.bin', '.apk'))))
        apk_hash = malware.replace(".apk", '').replace('.bin', '')
        if apk_hash in fail_list: continue
        if os.path.isfile(os.path.join(DYNAMIC_PROCESSED, apk_hash + '.finished')): continue

        # if os.path.exists(os.path.join(DYNAMIC_RESULTS, apk_hash)):
        #     continue
        # print("index: %d" % idx)
        pn = package_names[apk_hash] if apk_hash in package_names else get_package_name(os.path.join(ControlENV.SAMPLE_FOLDER_PATH, apk_hash + '.apk'))

        if apk_hash not in package_names or package_names[apk_hash] == "":
            package_names[apk_hash] = pn
        if pn == None: continue

        sample_list.append({
            'id': idx,
            'apk_name': apk_hash,
            'package_name': pn
        })

    with open('package_names.json', 'w') as f:
        json.dump(package_names, f)
    print("get app list ready, total length: %d" % len(sample_list))
    # print("invalid number: %d" % len([s for s in sample_list if s['package_name'] == None]))
    with open('invalid_samples.json', 'w') as f:
        json.dump([s['apk_name'] for s in sample_list if s['package_name'] == None], f)

    return sample_list



def generate_local_log(adb, message):
    if(isinstance(adb, ADB)):
        device = adb.device
        adb.log(message)
    else:
        device = adb
    
    if not os.path.exists("./dynamic_log"):
        os.mkdir('dynamic_log')
    with open("./dynamic_log/" + device + '_log.txt', "a") as f:
        f.write("%s %s %s\n" % (device, datetime.now(), message))



    # print(file_hash.digest())
    # print(file_hash.hexdigest())


def process_results(adb):
    package_name = adb.current_job.package_name
    sample_hash = adb.current_job.sample_hash
    level = adb.get_battery_level()
    dateTimeStr = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    os.system('echo "%s, %s, %s, %d" >> %s' %
            (dateTimeStr, adb.device, package_name, level, adb.device))

    # /PATH/TO/RESULTS/com.foo.goo
    result_path = os.path.join(DYNAMIC_RESULT_PATH, sample_hash)
    unpack_result_path = os.path.join(UNPACKER_DEX_PATH, sample_hash)
    if not os.path.exists(result_path):
        os.mkdir(result_path)
    if not os.path.exists(unpack_result_path):
        os.mkdir(unpack_result_path)

    proxy_destination_path = os.path.join(result_path, 'proxy')
    # /PATH/TO/BURP/reqlogs/com.foo.goo
    proxy_src_path = os.path.join(PROXY_RESULT_FOLDER, sample_hash)

    # /PATH/TO/BURP/reqlogs/no
    # no_path = os.path.join(burp_results, 'no')

    # Delete destination directory if present:
    if os.path.exists(proxy_destination_path):
        # print(
        #    "RUNNER: Deleting the result path to populate with new results!!")
        shutil.rmtree(proxy_destination_path)

    if os.path.exists(proxy_src_path):
        # Everything went well and we have results.
        print("RUNNER: moving results from %s to %s" % (proxy_src_path, proxy_destination_path))
        os.system('mv  %s %s' % (proxy_src_path, proxy_destination_path))
    else:
        pass
        # This didn't go as expected..
        # if os.path.exists(no_path):
        #    mv_src = no_path
    #TODO: copy json files from devices to result folder


    file_headers = ['WebView_', 'File_', 'URL_', 'Intent_']
    file_paths = [os.path.join('/sdcard', header + package_name + '.txt') for header in file_headers]

    lines = adb.execute_with_stdout("shell su -c \"ls /sdcard/\"")
    for l in lines:
        for f in file_paths:
            if f in l.decode(): 
                if l.startswith('WebView_'): adb.current_job.webview_log = l
                elif l.startswith('File_'): adb.current_job.file_log = l
                elif l.startswith('URL_'): adb.current_job.url_log = l
                elif l.startswith('Intent_'): adb.current_job.intent_log = l
    

    # file_path = os.path.join('/sdcard', package_name + '.txt')
    for file_path in file_paths:
        adb.execute("pull %s %s" % (file_path, os.path.join(result_path, file_path.split('/')[-1])))


    adb.execute('pull /data/local/tmp/%s.tar.gz %s' % (package_name, unpack_result_path))
    adb.execute('shell rm -rf /data/local/tmp/*.tar.gz')
    # adb.execute('shell su -c \"ls /sdcard/\"')
    adb.execute('shell rm -f -rR  /sdcard/*')
    adb.execute('shell su -c \"ls /sdcard/\"')

    # adb.execute('rm -rf /sdcard/*')

    # unpacked_file_lines = adb.execute_with_stdout("shell ls /data/data/%s" % package_name)
    # for f in unpacked_file_lines:
    #     if not f.endswith('.dex'): continue
    #     file_path = os.path.join('/data/data/%s' % package_name, f.strip())
    #     adb.execute('pull %s %s' % (file_path, unpack_result_path))



def handle_one_process(adb, process, q):
    # Start the process of running the app
    process.start()
    # Wait for 10 mins for the app to finfish
    process.join(timeout=10 * 60)

    def is_device_in_q(adb, q):
        return os.path.exists("%s.finished" % (adb.device))

    print("RUNNER: process timeout expired/or finished")
    # Did not end on it's own?
    phone_ip = adb.ip
    if process.is_alive():
        print(
            "RUNNER: ERROR. ERROR. timeout expired and process still running, killing.."
        )
        # -> terminate first
        #t.stop()
        time.sleep(2)
        #if not is_device_in_q(adb, q):
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.connect((phone_ip, 6002))
                s.close()
        except Exception as ex:
            print(ex)
            # print("nc reboot path failed?")
            # adb.execute('reboot recovery')

        time.sleep(20)
        # Delete /sdcard stuff
        adb.execute('shell rm -rf /sdcard/*')
        adb.execute('shell rm -rf /sdcard/.*')

        adb.execute('reboot')
        time.sleep(70)
    else:
        print("RUNNER: ended on it's own")


    level = adb.get_battery_level()
    if level < 5:
        # Wait sometime for the battery to charge
        adb.execute('reboot')
        print("Sleeping because battery is too low to run anything")
        time.sleep(30 * 60)
        adb.execute('reboot')
        time.sleep(70)

    # os.system("rm -rf %s.finished || true" % (adb.device))
    q.put(adb)


if __name__ == '__main__':
    exit(0)



