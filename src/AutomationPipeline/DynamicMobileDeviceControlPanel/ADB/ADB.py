import os, sys, re, shutil, socket
import threading
import subprocess
import time
import hashlib
from datetime import datetime 
from ENV import PACKAGE_TO_KEEP, AUTOLUNCHER_NAME, MITM_PROXY_ADDR

OFF = 'OFF'
ON_UNLOCKED = 'ON_UNLOCKED'
ON_LOCKED = 'ON_LOCKED'
UNKNOWN = 'UNKNOWN'


class ADB(object):
    def __init__(self, device=None):
        self.hub_location = None
        self.counter = 0
        self.device = device
        self.sadb = 'adb' if self.device is None else 'adb -s "%s"' % (self.device)
        self.fastboot = 'fastboot' if self.device is None else 'fastboot -s %s' % self.device
        self.ip = None
        self.log_file_path = ""
        self.awaitingAppResponse = False # autolauncher app alive check
        self.awaitingResponseTimer = 0 # autolauncher app alive check & malware alive check timer 
        self.doing_dynamic = False
        self.malware = None
        self.stop_timer = None

        self.parse_uhubctl() 
        
        # if not os.path.exists(self.device):
        #     os.system('echo "" > %s' % self.device)

   
    def set_malware(self, malware):
        self.malware = malware
        os.system('echo %s >> %s' % (malware.sample_hash, self.device)) 
        
    def reset(self):
        self.log_file_path = ""
        self.awaitingAppResponse = False
        self.awaitingResponseTimer = 0
        self.doing_dynamic = False
        self.stop_timer = None
        self.malware = None

    def is_device_absent_or_offline(self):
        # This returns true if _this_ device was once present,
        # but is now AWOL or offline.
        out = subprocess.check_output(['adb', 'devices']).splitlines()
        found = False
        status = False

        for line in out[1:]:
            l = line.decode().strip()
            if not l:
                continue

            if self.device in l:
                found = True
                if 'offline' in l:
                    status = True
                break

        return status or not found

    def remove_unknown_packages(self):
        # Remove unknown packages
        data = self.execute_with_stdout(" shell pm list packages -e")
        #did_uninstall = False
        package_to_remove = set()
        for line in data:
            l = line.decode().strip().replace('package:', '')
            if l not in PACKAGE_TO_KEEP:
                package_to_remove.add(l)
        print('pkg to remove: ' + str(package_to_remove))
        for pkg in package_to_remove:
            self.uninstall_apk(pkg)
                #did_uninstall = True
        time.sleep(1)
        
        # 
            # Sleep and wait till the device reboots. (we wallclock timed the
            # reboot of Nexus 6 at 1min :07.90 seconds)
            #time.sleep(70)

    def install_apk(self, apk_path, no_retry=False):
        if not os.path.exists(apk_path):
            raise Exception('APK not found: %s' % apk_path)

        self.execute('push %s /data/local/tmp/target.apk' % apk_path)
        self.execute('shell settings put global package_verifier_enable 0')

        lines = self.execute_with_stdout('shell pm install -t -g /data/local/tmp/target.apk',
                            timeout=60, read_lines = True)
        
        success = self.is_app_installed(self.malware.package_name)
        for(i, line) in enumerate(lines):
            l = line.decode().strip()
            if 'Success' in l:
                success = True
                break
      
        if not success: 
            if self.malware != None:
                if not lines:
                    self.log("NO INSTALL INFO")
                    self.malware.install_error = "NO INSTALL INFO"
                else:
                    error = '\n'.join([line.decode().strip() for line in lines])
                    if 'INSTALL_FAILED_INSUFFICIENT_STORAGE' in error:
                        if not no_retry:
                            self.reboot()
                            return self.install_apk(apk_path, no_retry=True)
                        else:
                            self.log("INSTALL_FAILED_INSUFFICIENT_STORAGE")
                            self.malware.install_error = "INSTALL_FAILED_INSUFFICIENT_STORAGE"
                    else:
                        self.log("INSTALL ERROR: " + error)
                        self.malware.install_error = error

        return success

    def uninstall_apk(self, apk_name):
        if self.is_app_installed(apk_name):
            self.execute('shell su -c "pm uninstall %s"' % apk_name)
            self.execute('shell su -c "pm uninstall --user 0 %s"' % apk_name)
            return True

    def plug_on_off(self):
        # Uses the command `uhubctl` to turn a usb port off and then
        #  switch it back again. Usually done in cases when the devices goes
        #  missing or offline.

        # print("RUNNER: plugging %s on/off" % self.device)
        hub_location = self.hub_location
        subprocess.check_output([
            'uhubctl', '--action', 'off', '-p', hub_location["port"],
            '-l', hub_location["location"]
        ])
        time.sleep(5)
        subprocess.check_output([
            'uhubctl', '--action', 'on', '-p', hub_location["port"],
            '-l', hub_location["location"]
        ])
        time.sleep(5)
    

        

    def log(self, message):
        if not self.log_file_path:
            return
            
        if message == None: 
            return 
        if not message.endswith("\n"):
            message = message + '\n'
        with open(self.log_file_path, 'a') as f:
            f.write(message)
        print(datetime.now().strftime("%m/%d/%Y, %H:%M:%S") + " " +  self.device + ": " + message.strip())



    def is_app_installed(self, app_name):
        """Returns True if an app is installed on the device.
        :param str app_name: name of the app to be checked.
        """
        data = self.execute_with_stdout(" shell pm list package %s" % app_name)
        for line in data:
            l = line.decode().strip()
            if l == 'package:%s' % (app_name):
                return True
        return False

    
    def get_battery_level(self):
        levelLine = ''

        data = self.execute_with_stdout(" shell dumpsys battery")
        for line in data:
            l = line.decode().strip()
            if 'level' in l:
                levelLine = l
                break
        if levelLine == '':
            return 50
        return int(levelLine.split(' ')[1])

    def reboot(self):
        self.execute('reboot')
        time.sleep(60)


    def execute(self, cmd, timeout=100, fastboot=False):
        # RAISES subprocess.TimeoutExpired is process expires the given timeout.
        #print('ADB: %s %s' % (self.sadb, cmd))
        if self.is_device_absent_or_offline():
            self.plug_on_off()
        
        p = subprocess.Popen('%s %s' % (self.sadb, cmd),
                             stderr=subprocess.STDOUT,
                             shell=True)
        try:
            if timeout is not None:
                p.wait(timeout=timeout)
            else:
                p.wait()
            return p.returncode
        except subprocess.TimeoutExpired:
            p.terminate()
        
            # print("ADB: timeout expired")
            return -1


    def execute_with_stdout(self, cmd, read_lines=True, timeout=800):
        if self.is_device_absent_or_offline():
            self.plug_on_off()

        adb_cmd = '%s %s' % (self.sadb, cmd)

        ps = subprocess.Popen(adb_cmd,
                            stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE,
                            shell=True)

        try:
            ps.wait(timeout=timeout)
        except:
            ps.terminate()
            # print("ADB: timeout expired")
            return ""
        
        if read_lines:
            return ps.stdout.readlines()
        else:
            return ps.stdout.read()
        

    def getip(self):
        res = self.execute_with_stdout('shell ifconfig wlan0',
                                    read_lines=False).decode()
        print(self.device)
        print(res)
        
        if 'mask' in res:  # old device
            return res.split('mask')[0].split('ip')[1].strip()
        elif res.startswith('wlan0'):  # new device
            return res.splitlines(False)[1].split('Bcast')[0].split('addr:')[1].strip()
    

    def get_screen_status(self):

        res = self.execute_with_stdout('shell dumpsys nfc | grep "mScreenState=" ', read_lines=False)

        for i in [OFF, ON_UNLOCKED, ON_LOCKED]:
            if i in str(res):
                return i

        return UNKNOWN

    def make_sure_net(self):
        tries = 10
        while tries != 0:
            tries -= 1
            res = self.execute_with_stdout('shell ping -c1 8.8.8.8',
                                           read_lines=False).decode()
            if 'ttl=' in res:
                print("has internet")
                return
            else:
                print('waiting for internet on')
                time.sleep(5)

    def turn_screen_on(self):
        # makesurenet(adb)

        tries = 10
        while self.get_screen_status() == UNKNOWN:
            if tries < 0:
                return -1
            time.sleep(1)
            print('waiting for power on %d' % tries)
            tries -= 1

        self.make_sure_net()

        time.sleep(1)
        self.execute('shell input keyevent 26')
        time.sleep(1)

        status = self.get_screen_status()
        if status == OFF:
            self.execute('shell input keyevent 26')
            time.sleep(1)

        status = self.get_screen_status()
        if status == ON_LOCKED:
            self.execute('shell input keyevent 82')
            time.sleep(1)

        return 1
    
    def turn_screen_off(self):
        tries = 10
        while self.get_screen_status() == UNKNOWN:
            if tries < 0:
                return -1
            time.sleep(1)
            print('waiting for power off %d' % tries)
            tries -= 1
        
        status = self.get_screen_status()
        if status == ON_UNLOCKED or status == ON_LOCKED:
            self.execute('shell input keyevent 26')
            time.sleep(1)

        return 1


    def make_sure_screen_is_on(self):
        ret = 1
        tries = 10
        while self.get_screen_status() != ON_UNLOCKED:
            tries -= 1
            if tries == 0:
                return -1

            print('waiting for screen on')
            ret = self.turn_screen_on()
            if ret < 0:
                return ret

            time.sleep(1)

        print('screen is on')
        return ret

    def start_experiment(self, package_name):

        self.doing_dynamic = True
        if self.stop_timer != None:
            self.stop_timer.cancel()

        self.stop_timer = threading.Timer(20.0, self.finish_experiment, (False, "Stopped by Controller Timer"))
        self.stop_timer.start()

    def finish_experiment(self, end_normally = True, message = ""):
        if self.stop_timer != None: 
            self.stop_timer.cancel()
            self.stop_timer = None
        if self.doing_dynamic == True: 
            self.doing_dynamic = False
        self.log("Finished with " + ("SUCCESS" if end_normally else "FAIL") + "  " + message if message is not None else "")
    


    def reset_stop_timmer(self):
        if self.stop_timer != None:
            self.stop_timer.cancel()
            self.stop_timer = threading.Timer(20.0, self.finish_experiment, (False, "Stopped by Controller Timer"))
            self.stop_timer.start()

    def is_frida_server_running(self):
        frida_cmd = 'frida-ps -D {} | grep \"frida-server\"'.format(self.device)
        ps = subprocess.Popen(frida_cmd,
                            stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE,
                            shell=True)
        try:
            out, err = ps.communicate(timeout=10)
            outstr = str(out.decode('utf-8'))
            if 'frida-server' in outstr:
                return True
            
        except Exception as e:
            print(e)
            ps.terminate()
            self.reboot()
            return False
        return False
    

    def start_app(self, package_name):
        self.execute("shell monkey -p " + package_name +  " -c android.intent.category.LAUNCHER 1")

    def stop_app(self, package_name):
        self.execute("shell am force-stop " + package_name)

    def start_autolauncher(self):
        self.start_app(AUTOLUNCHER_NAME)
    
    def push_file(self, src, dist):
        name_hash = hashlib.md5(src.encode()).hexdigest()
        self.execute("push %s /sdcard/tmpp_%s.tmp" % (src, name_hash))
        self.execute("shell su -c \"mv /sdcard/tmpp_%s.tmp %s\"" % (name_hash, dist))
        self.execute("shell su -c \" chmod 777 %s\"" % dist)

    def pull_file(self, src, dist):
        name_hash = hashlib.md5(src.encode()).hexdigest()
        self.execute("shell su -c \"cp %s /sdcard/tmp_%s.tmp\"" % (src, name_hash))
        self.execute("pull /sdcard/tmp_%s.tmp %s" % (name_hash, dist))
        
    def start_frida_server(self):
        if self.is_frida_server_running():
            return
        lines = self.execute_with_stdout("shell ls /data/local/tmp/frida-server")
        if len(lines) == 0 or lines[0].decode().strip() != '/data/local/tmp/frida-server':
            self.execute("push ./frida-server /data/local/tmp/frida-server")
        self.execute("shell su -c \"chmod +x /data/local/tmp/frida-server\"")
        self.execute("shell su -c \"/data/local/tmp/frida-server &\" &")
        time.sleep(2)
        if self.is_frida_server_running():
            print("LOG: frida server started" + self.device)
        else:
            print("ERROR: fail to start frida server "  + self.device )
        return

    def set_autolauncher_socket_io_server(self, server:str):
        self.execute_with_stdout("shell \"echo  \\\" {\\\\\\\"%s\\\\\\\":\\\\\\\"%s\\\\\\\"} \\\"  > /data/local/tmp/serverUrl.json\"" % (self.device, server))

    def disable_auto_rotation(self):
        self.execute("shell settings put system accelerometer_rotation 0")
    # def start_app_with_frida_inject(self, packageName):
    #     try:
    #         frida_inject_unpack_code_and_start_app(packageName, self.device)
    #     except Exception as e:
    #         print(e)
    
    
    @staticmethod 
    def get_devices(adb_path='adb', device_list=None):
        with open(os.devnull, 'wb') as devnull:
            subprocess.check_call([adb_path, 'start-server'],
                                stdout=devnull,
                                stderr=devnull)
        out = subprocess.check_output([adb_path, 'devices']).splitlines()
        # The first line of `adb devices` just says "List of attached devices", so
        # skip that.
        devices = []
        for line in out[1:]:
            l = line.decode().strip()
            if not l:
                continue
            if 'offline' in l:
                continue
            serial, _ = re.split(r'\s+', l, maxsplit=1)
            if not device_list or  serial in device_list:
                devices.append(serial)
        return devices

    def set_proxy(self):
        self.execute('shell su -c \"settings put global http_proxy {}\"'.format(MITM_PROXY_ADDR))
        time.sleep(1)
    
    def reset_proxy(self):
        self.execute('shell su -c \"settings put global http_proxy :0\"')
        time.sleep(1)
    
    def parse_uhubctl(self):
        # os.system("sudo usb-reset -a")
        # time.sleep(5)
        # parse output from the `uhubctl` command to form a association between
        #  devices to their particular port and hub location.

        cmd = 'uhubctl'
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
            if device == self.device:
                self.hub_location = {
                    "port": lsplit[1].replace(':', ''),
                    "location": current_hub
                }

    
    def enable_wifi(self):
        self.execute('shell su -c \"svc wifi enable\"')
        time.sleep(2)
    
    def disable_wifi(self):
        self.execute('shell su -c \"svc wifi disable\"')
        time.sleep(2)
        
    
# if __name__ == '__main__':
#     adb = ADB('test device')
#     adb.stop_timer.cancel()
#
#     exit()
