package com.cyfi.XposedPlugin.dynamic.control;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Timer;

public class ControlUtils {

    public static void surun(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            p.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void runCmd(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{cmd});
            p.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public static void stopApp(String packageName ){
        surun("am force-stop " + packageName);
    }

    public static void startApp(String packageName){
        surun("monkey -p " + packageName +  " -c android.intent.category.LAUNCHER 1");
    }

    public static void uninstallApp(String packageName){
        surun("pm uninstall " + packageName);
    }

    public static void installApp(String path){
        surun("pm install -r " + path);
    }

    public static void monkeyrunApp(String packageName){
        surun("monkey -p " + packageName + " --throttle 200 -v 500  --pct-touch 100 --pct-syskeys 0 > /dev/null 2>&1");
    }

    public static void resetExperiment(String packageName){
        try{
            stopApp(packageName);
            Thread.sleep(2000);
            uninstallApp(packageName);
            Thread.sleep(2000);
            installApp("/sdcard/malware.apk");
            Thread.sleep(2000);
            startApp(packageName);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void threadSleep(float seconds){
        try {
            int millseconds = (int)(seconds * 1000);
            Thread.sleep(millseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void clearTimer(Timer timer){
        if(timer != null) {
            timer.cancel();
            timer.purge();
        }
    }


    public static boolean isAppRunning(final Context context, final String packageName) {
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        if (procInfos != null)
        {
            for (final ActivityManager.RunningAppProcessInfo processInfo : procInfos) {
                if (processInfo.processName.equals(packageName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void writeSDFile(String fileName, String write_str) throws IOException {


        String path = Environment.getExternalStorageDirectory().getPath();
        File file = new File(path  + '/' +  fileName);
        FileOutputStream fos = null;

        try{
            if(file.exists()){
                file.delete();
            }
            file.createNewFile();
            fos = new FileOutputStream(file, true);
        }
        catch(FileNotFoundException e){
            if(e.getLocalizedMessage().contains("Permission denied")){
                ControlUtils.surun("pm grant com.cyfi.autolauncher2 android.permission.WRITE_EXTERNAL_STORAGE");
            }
            file.createNewFile();
            fos = new FileOutputStream(file, true);
        }catch(Exception e){
            e.printStackTrace();
        } finally {
            if(fos == null){
                Log.e("writeSDFile", "fos is null");
            }else{
                if(!write_str.endsWith("\n")) write_str = write_str + "\n";
                byte [] bytes = write_str.getBytes();

                fos.write(bytes);

                fos.close();
            }


        }


    }


}
