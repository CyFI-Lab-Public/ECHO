import sys, os, queue, time, json
import threading
import logging
from urllib.response import addbase
import click
import traceback
import datetime
from bson import json_util
from flask import Flask, jsonify, request, render_template, url_for, redirect
from flask_socketio import SocketIO, send, emit, join_room
from Malware import Malware
from mongodb import MalwareDB
from GleanMongoDB.MalwareSampleFeeder import MalwareSampleQueue

from Utils import get_devices, get_package_name, init_adb_queue, \
    generate_local_log, start_proxy, clear_previous_proxy_results, process_results, handle_one_process

from DynamicProcessing.ADB.ADB import ADB
from ControlENV import *

MANUAL_MODE = False

app = Flask(__name__, template_folder=FLASK_TEMPLATE_FOLDER)

log = logging.getLogger('werkzeug')
log.setLevel(logging.ERROR)
def secho(text, file=None, nl=None, err=None, color=None, **styles):
    pass

def echo(text, file=None, nl=None, err=None, color=None, **styles):
    pass

click.echo = echo
click.secho = secho


socketApp = SocketIO(app, cors_allowed_origins="*", ping_timeout=1200, ping_interval=5)

sample_queue = MalwareSampleQueue()
malwareDB = MalwareDB()

# Page
@app.route('/', methods=['GET'])
@app.route('/page/monitor', methods = ['GET'])
def page_monitor():
    return render_template('dynamicMonitor.html')

# RESTful API


@app.route('/api/restore', methods=['POST'])
def restore():
    devices = get_devices()
    adbs = [ADB(device) for device in devices]

    threads = []
    for adb in adbs:
        t = threading.Thread(target=adb.restore)
        threads.append(t)

    for t in threads:
        t.start()

    for t in threads:
        t.join()
    return jsonify({
        "success": True
    })

# debug purpose
@app.route('/api/broadcast', methods=['POST'])
def bd():
    broadcast(True, 'device_name', None)
    return jsonify({
        "success": True
    })

@app.route('/api/install_test', methods=['POST'])
def install_test():
    with open('/home/cyfi/workplace/ProjDropperChain/DynamicProcessing/ICS_APP/install_fail.json', 'r') as f:
        app_hash_list = json.load(f)
    test_app_install(app_hash_list)
    return jsonify({
        "success": True
    })
    



#debug purpose
@app.route('/api/broadcast/<device_name>', methods =['POST'])
def bd_room(device_name):
    broadcast(True, 'test', None, room=device_name)
    return jsonify(({'success': True}))

@app.route('/api/test/mal_running/<device_name>', methods = ['POST'])
def test_mal_running(device_name):
    print('name: ' + device_name)
    send_is_mal_running("com.cyfi.autolauncher2", room=device_name)
    return jsonify({'success': True})


@app.route('/api/app_check/<device_name>', methods= ['POST'])
def api_app_check(device_name):
    send_app_check(device_name)
    return jsonify({
        "success": True
    })


@app.route('/api/proxy_check/<device_name>', methods= ['POST'])
def api_proxy_check(device_name):
    send_proxy_check(device_name)
    return jsonify({
        "success": True
    })



# start dynamic processing
@app.route('/api/dynamic', methods=['POST'])
def dynamic():
    print("start dynamic")

    start_dynamic()
    return jsonify({
        "success": True
    })

# Monitor api
@app.route('/api/monitor', methods=['POST'])
def monitor():
    try:
        jobs = {k: (v.current_job.to_dict() if v.current_job is not None else Malware(k).to_dict() ) for k, v in adbs_dict.items()}
        return jsonify({
            'success': True,
            'jobs': jobs
        })   
    except Exception as e:
        traceback.print_exc()
        return jsonify({
                'success': False,
                'error': str(e)
            })


@app.route('/api/monitor/history/<device_name>', methods=['POST'])
def get_history_for_device(device_name):
    try:
        if device_name == 'all':
            names = list(adb_dict.keys())
        else:
            names = [device_name]
        
        histories = {}
        for name in names:
            histories[name] = malwareDB.query({'device': name}, sort_key='start_time', reverse=True)[:10]
        return json_util.dumps({
            'success': True,
            'histories': histories
        })   
        
    except Exception as e:
        traceback.print_exc()
        return jsonify({
                'success': False,
                'error': str(e)
            })



#MANUAL_MODE APIS:
@app.route('/api/manual/get_app/<device_name>', methods = ['GET'])
def manual_get_app_for_device(device_name):
    response_content = {}

    if device_name not in adbs_dict:
        print("ERROR: Restart the pipeline to reigster the device")
        response_content['success'] = False
        response_content['message'] = "ERROR: Restart the pipeline to reigster the device"
        return jsonify(response_content)
    else:
        adb = adbs_dict[device_name]
        if adb == None:
            print("ERROR: Restart the pipeline to reigster the device")
            response_content['success'] = False
            response_content['message'] = "ERROR: Restart the pipeline to reigster the device"
            return jsonify(response_content)

    global sample_list
    if len(sample_list) == 0:
        response_content['success'] = False
        response_content['message'] = "ERROR: No App to Process"
        return jsonify(response_content)
    sample_param = sample_list.pop()

    get_valid_adb_testbed(sample_param, adb)
    adb.set_current_job(sample_param)
    apk_hash = adb.current_job.sample_hash
    package_name = adb.current_job.package_name
    install_app(adb)
    send_install_done(package_name, apk_hash, device_name)
    

    response_content['success'] = True
    return jsonify(response_content)


@app.route('/api/manual/start/<device_name>', methods = ['GET'])
def manual_start_experiment(device_name):
    response_content = {}

    if device_name not in adbs_dict:
        print("ERROR: Restart the pipeline to reigster the device")
        response_content['success'] = False
        response_content['message'] = "ERROR: Restart the pipeline to reigster the device"
        return jsonify(response_content)
    else:
        adb = adbs_dict[device_name]
        if adb == None:
            print("ERROR: Restart the pipeline to reigster the device")
            response_content['success'] = False


            response_content['message'] = "ERROR: Restart the pipeline to reigster the device"
            return jsonify(response_content)

    package_name = adb.current_job.package_name
    sample_hash = adb.current_job.sample_hash
    print("current hash: {}".format(sample_hash))
    send_start_app(package_name, sample_hash, room=adb.device)

    response_content['success'] = True
    return jsonify(response_content)



@app.route('/api/manual/finish/<device_name>', methods = ['GET'])
def manual_finish_experiment(device_name):
    response_content = {}

    if device_name not in adbs_dict:
        print("ERROR: Restart the pipeline to reigster the device")
        response_content['success'] = False
        response_content['message'] = "ERROR: Restart the pipeline to reigster the device"
        return jsonify(response_content)
    else:
        adb = adbs_dict[device_name]
        if adb == None:
            print("ERROR: Restart the pipeline to reigster the device")
            response_content['success'] = False
            response_content['message'] = "ERROR: Restart the pipeline to reigster the device"
            return jsonify(response_content)

    package_name = adb.current_job.package_name
    sample_hash = adb.current_job.sample_hash
    clear_app(adb)

    process_results(adb, package_name, sample_hash)
    generate_local_log(adb, "Done: result:" + str(True))

    os.system("touch {}".format(os.path.join(DYNAMIC_PROCESSED, sample_hash + '.finished')))

    send_finish_done(adb.device)
    adb.current_job = None

    response_content['success'] = True
    return jsonify(response_content)


# Socket API

@socketApp.on("connect")
def socket_connect():
    print("socket connected")


@socketApp.on("disconnect")
def socket_disconnect():
    print("socket disconnected")


#Command in List:
#   log: print log in the terminal
#   job_finish: end of analyzing an app
#   device_name: take device_name response, allocate room for socket
#   app_check: take app_check response, set app as available on device

@socketApp.on("message")
def socket_receive_message(m):
    message = m if isinstance(m, dict) else json.loads(m)
    try:
        success = message['success']
        command = message['command']

        if command == "log":
            print("LOG: %s" % message['contents']['content'])
            device_name = message['contents']['device']
            adb=adbs_dict[device_name]
            adb.reset_stop_timmer()

        elif command == "job_finish":
            device_name = message['contents']['device_name']
            package_name = message['contents']['package_name']
            adbs_dict[device_name].doing_dynamic = False
            adbs_dict[device_name].current_job.crash = False

            generate_local_log(adbs_dict[device_name], "Receive job_finish for " + package_name)
            print("job finish: " + package_name)

        elif command == "device_name":
            device_name = message['contents']['device_name']
            if device_name not in adbs_dict.keys():
                print("Error: no such device!")
                # return
            join_room(device_name)
            print("join room: device name : %s" % device_name)

        elif command == "app_check":
            print("app check done")
            device_name = message['contents']['device_name']
            adbs_dict[device_name].awaitingAppResponse = False

        elif command == 'mal_running':
            print("mal running")
            device_name = message['contents']['device_name']
            if device_name not in adbs_dict.keys():
                print("Error: no such device!") 

            adbs_dict[device_name].awaitingResponseTimer = 0
            res = message['contents']['mal_running'] == "1"
            if not res:
                adbs_dict[device_name].finish_experiment(False, "Auto Launcher Not Running")
                generate_local_log(adbs_dict[device_name], "Auto Launcher Died")

    except Exception as e:
        print(e)
        return

# Command out list
#   app_check: check whether the autoLauncher2 app is alive
#   proxy_check: check whether the proxy is alive
#   device_name: register device name with socket
#   start_app: start to analysis an app

def send_install_done(package_name, sample_hash, room):
    # broadcast(True, "install_done", None, room=room)
    broadcast(True, "install_done", {"package_name": package_name, "sample_hash": sample_hash}, room=room)

def send_finish_done(room):
    broadcast(True, "finish_done", None, room=room)

def send_app_check(room=None):
    if room is None:
        _ = [adb.awaitingAppResponse for adb in adbs_dict.values()]
        broadcast(True, "app_check", None)
    else:
        adbs_dict[room].awaitingAppResponse = True
        broadcast(True, "app_check", None, room=room)


def send_proxy_check(room=None):
    if room is None:
        broadcast(True, "proxy_check", None)
    else:
        broadcast(True, "proxy_check", None, room=room)


def send_device_name(room=None):
    if room is None:
        broadcast(True, "device_name", None)
    else:
        broadcast(True, "device_name", None, room=room)


def send_start_app(package_name, sample_hash, room):
    broadcast(True, "start_app", {"package_name": package_name, "sample_hash": sample_hash}, room=room)

def send_is_mal_running(package_name,room):
    broadcast(True, "mal_running", {"package_name": package_name}, room=room)


def response(success, command, contents, room):
    send(json.dumps({"success": success, "command": command, "contents": contents}), to=room)




# functional API

def test_app_install(app_list):
    adb = q.get()
    results = {}
    for app_hash in app_list:
        print("=====================================================")
        print(app_hash)
        app_path =os.path.join(SAMPLE_FOLDER_PATH, app_hash + '.apk')
        adb.execute('push %s /sdcard/target.apk' % app_path)
        adb.execute('shell settings put global package_verifier_enable 0')
        lines = adb.execute_with_stdout('shell pm install -t /sdcard/target.apk',
                        timeout=60, read_lines=True)

        results[app_hash] = lines[1].decode()
        time.sleep(1)
        with open('test_install.json', 'w') as f:
            json.dump(results, f)
    return

# AUTO_MODE FUNCTIONS:
def start_dynamic():
    sample_queue.get_static_sample_list()
    while True:
        sample = sample_queue.get_sample()

        if os.path.exists(os.path.join(DYNAMIC_PROCESSED, sample['apk_name'] + '.finished')):
            continue

        generate_local_log("general", "put job id: %s" % str(sample['id']))
        print("put job: %s" % sample['apk_name'])
        adb = get_valid_adb_testbed(sample)
        adb.reset()
        execute_dynamic_for_single_sample(sample, adb)

        time.sleep(1)


def get_valid_adb_testbed(param, adb=None, check_proxy = True, check_autolauncher = True):
    apk_hash = param['apk_name']
    package_name = param['package_name']

    print("========= Start Processing:" + apk_hash)

    def check_autolauncher_online(adb):
        # packagename = 'appcheck::' + adb.device
        adb.awaitingAppResponse = True
        send_app_check(room=adb.device)

        adb.awaitingResponseTimer = 0
        while adb.awaitingResponseTimer < 5 and adb.awaitingAppResponse:
            adb.awaitingResponseTimer += 1
            time.sleep(1)
        if adb.awaitingAppResponse == True:
            send_device_name()
            time.sleep(1)
            send_app_check(room=adb.device)

        res = not adb.awaitingAppResponse
        adb.awaitingAppResponse = False
        return res

    def start_proxy_helper(q):
        print("RUNNER: ERROR PROXY STOPPED WORKING!!")
        # wait for running tasks to complete
        while not q.full():
            time.sleep(1)

        start_proxy()
        time.sleep(40)

    def is_proxy_alive(adb, results_path):
        print("proxy alive check..")
        packagename = 'infiltrate_' + adb.device

        check_dir = os.path.join(results_path, packagename)
        send_proxy_check(room=adb.device)

        time.sleep(1)
        # if the proxy was alive it would have created this directory
        if os.path.exists(check_dir):
            os.system('rm -rf %s' % check_dir)
            return True

        print("RUNNER: ERROR: proxy stopped working")
        return False

    if package_name is None:
        print("invalid package name, skip")
        return

    if adb == None:
        adb = q.get()

    adb.set_current_job(param)

    generate_local_log(adb, "start job: " + apk_hash + " " + package_name)

    if check_autolauncher and (not check_autolauncher_online(adb)):
        print("ERROR: Launcher is not in active for:" + adb.device)
        generate_local_log(adb, "ERROR: Launcher is not in active")

        adb.execute('reboot')
        time.sleep(70)

    if check_proxy and (not is_proxy_alive(adb, PROXY_RESULT_FOLDER)):
        # reboot and try again:
        adb.execute('reboot')
        time.sleep(50)

        if not is_proxy_alive(adb, PROXY_RESULT_FOLDER):
            generate_local_log(adb, "ERROR: Proxy Down")
            q.put(adb)
            start_proxy_helper(q)
            time.sleep(60)
            adb = q.get()
    return adb




def execute_dynamic_for_single_sample(param, adb):
    apk_hash = param['apk_name']
    package_name = param['package_name']
    adb.set_current_job(param)
    adb.current_job.status = 'initializing device'
    malwareDB.update(adb.current_job)
    

    t = threading.Thread(target=start_experiment_thread_wrapper,
                        args=(adb,))

    ht = threading.Thread(target=handle_one_process, args=(adb, t, q))
    ht.start()


def start_experiment_thread_wrapper(adb):
    adb.remove_unknown_packages()
    sample_hash = adb.current_job.sample_hash
    package_name = adb.current_job.package_name

    #
    # if adb.counter > RESTORE_COUNTER:
    #     adb.restore()
    #     adb.counter = 0
    adb.make_sure_screen_is_on()
    if adb.make_sure_screen_is_on() < 0:
        adb.execute('reboot')
        time.sleep(50)
        # time.sleep(10)
        # return False
    adb.execute('shell settings put system screen_brightness_mode 0')
    adb.execute('shell settings put system screen_brightness 10')
    adb.start_frida_server()

    
    clear_previous_proxy_results(package_name)

    print("RUNNER: processing %s..." % package_name)

    success = install_app(adb)
    if not success:
        generate_local_log(adb, "LOG: FAIL TO INSTALL APP")
        adb.current_job.status = 'install fail'
    else:
        success = execute_app(adb)

    print("RUNNER: DONE: %s..." % package_name)
    if not success:
        print("RUNNER: DONE WITH FAILURE")
        # os.system("touch {}".format(os.path.join(DYNAMIC_PROCESSED, apk_hash + '.finished')))
        # return
    process_results(adb)
    clear_app(adb)
    malwareDB.update(adb.current_job)


    generate_local_log(adb, "Done: result:" + str(success))
    os.system("touch {}".format(os.path.join(DYNAMIC_PROCESSED, sample_hash + '.finished')))
 

#Step 1: Install app on the device
def install_app(adb):
    sample_hash = adb.current_job.sample_hash
    package_name = adb.current_job.package_name

    adb.doing_dynamic = False

    apk_name = sample_hash + '.apk'
    apk_path = os.path.join(SAMPLE_FOLDER_PATH, apk_name)
    
    adb.install_apk(apk_path)

    success = False
    if not adb.is_app_installed(package_name):
        generate_local_log(adb, "ERROR: Fail to install apk")
        os.system("echo \"Install Fail\" >> {}".format(os.path.join(DYNAMIC_PROCESSED, sample_hash + '.finished')))
        print("RUNNER: app is not installed")
        adb.current_job.can_install = False
        success =  False
    else:
        adb.current_job.can_install = True
        generate_local_log(adb, "LOG: apk install success")
        success = True
        malwareDB.update(adb.current_job)

    return success

#Step 2: Execute the App, notify test bed to start the
def execute_app(adb):
    sample_hash = adb.current_job.sample_hash
    package_name = adb.current_job.package_name

    adb.counter += 1
    adb.current_job.start_time = datetime.datetime.utcnow()
    malwareDB.update(adb.current_job)

    # start app in dynamic analysis tool

    send_start_app(package_name, sample_hash, adb.device) # just notify the on-device controller, not started from there, start the app with frida spawn
    time.sleep(1)
    adb.current_job.status = 'running'
    malwareDB.update(adb.current_job)

    adb.start_experiment(package_name)
    generate_local_log(adb, "LOG: Start Experiment " + package_name)
    # # wait for 600 seconds for a complete run
    # a timer interuptable by api call: GET /api/finish/<device>
    total_timing = 600


    while total_timing > 0 and adb.doing_dynamic and adb.awaitingResponseTimer < 5:
        time.sleep(1)
        if total_timing % 5 == 0: send_is_mal_running(package_name, adb.device)
        total_timing -= 1
        adb.awaitingResponseTimer += 1
    if adb.doing_dynamic and adb.awaitingResponseTimer >= 5: # auto launcher has no response, but not stop
        adb.finish_experiment(False, "AUTO LAUNCHER DOWN")
    elif total_timing <= 0 and adb.doing_dynamic: # time out 
          adb.finish_experiment(False, "Time Out")
    else: # finished normally 
        adb.finish_experiment(True, "Finish Normally")
        #: additional: killed by ADB stoptimmer, as no more logs 
  
    print("RUNNER: dynamic finished")
    generate_local_log(adb, "Done: Dynamic finished")
    adb.current_job.end_time = datetime.datetime.utcnow()
    adb.current_job.status = 'dynamic_finish'
    malwareDB.update(adb.current_job)

    return True

#Step 3: Clear the app from the device
def clear_app(adb):
    package_name = adb.current_job.package_name

    print("RUNNER: uninstall app:")
    adb.execute("shell am force-stop " + package_name)
    time.sleep(2)
    adb.execute('uninstall %s' % package_name)
    time.sleep(5)
    if adb.is_app_installed(package_name):
        adb.execute('uninstall %s' % package_name)
        time.sleep(5)
        if adb.is_app_installed(package_name):
            generate_local_log(adb, "Error: Fail to uninstall, need restore ")
            print("RUNNER: app uninstall failed, do restore")
            # adb.restore()
    generate_local_log(adb, "LOG: uninstall success")
    time.sleep(10)
    return True


q, adbs_dict, device_num = init_adb_queue()
# # init as main function
# start_proxy()

# broadcast(True, 'device_name', None)
# socketApp.run(app, host="192.168.2.118", port='5000')

if __name__ == '__main__':
    socketApp.run(app, host="0.0.0.0", port=5000)
    pass
