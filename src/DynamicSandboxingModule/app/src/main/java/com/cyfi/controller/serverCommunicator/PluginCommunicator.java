package com.cyfi.controller.serverCommunicator;

// Responsible for control the experiment process, initialized after a new job is received
// A child of Server Communicator, keep a weak reference
//

import static java.lang.Thread.sleep;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.cyfi.ENV;
import com.cyfi.XposedPlugin.dynamic.control.ControlUtils;
import com.cyfi.autolauncher2.DCLResult;
import com.cyfi.autolauncher2.JSIResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

enum ExperimentStatus {
    idle,
    running,
    monkey
}

public class PluginCommunicator extends BroadcastReceiver {

    private final WeakReference<ServerCommunicator> serverCommunicatorWeakReference;
    private final WeakReference<Activity> activityWeakReference;
    private final String packageName;
    private final String deviceName;
    private Timer aliveMonitor;
    private Timer timeoutMonitor;
    private ExperimentStatus experimentStatus;
    private Integer aliveNoResponceCounter;
    private Integer crashCounter;

    @SuppressLint("HardwareIds")
    PluginCommunicator(ServerCommunicator sc, String packageName, Activity activity){
        this.serverCommunicatorWeakReference = new WeakReference<>(sc);
        this.packageName = packageName;
        this.deviceName = Build.SERIAL;
        this.activityWeakReference = new WeakReference<>(activity);
        this.experimentStatus = ExperimentStatus.idle;
        activity.registerReceiver(this, new IntentFilter(ENV.intentPatternFromPlugin));
        this.aliveNoResponceCounter = 0;
        this.crashCounter = 0;
    }

// Receive any communication from Xposed plugin
    @Override
    public void onReceive(Context context, Intent intent) {
        System.out.println("Received intent from plugin");

//        if(this.experimentStatus != ExperimentStatus.running) return;
        String command = intent.getStringExtra("bcType");
        if(command == null || command.equals("")) return;
        log("current command: " + command);

        switch(command){
            case "APP_STARTED":
                this.experimentStatus = ExperimentStatus.running;
                startAliveMonitor();
                startTimeoutMonitor();
                this.serverCommunicatorWeakReference.get().sendFlowDroidResultToPlugin();
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                sendBroadCastToPlugin("START_EXPERIMENT");
                break;
            case "EXPERIMENT_FINISHED":
                this.endExperiment();
                break;
            case "ALIVE":
                this.aliveNoResponceCounter = 0;
                break;
            case "CRASH":
                ControlUtils.threadSleep(1000);

                if(!ControlUtils.isAppRunning(this.activityWeakReference.get() ,this.packageName)){
                    ControlUtils.threadSleep(1000);
                    crashCounter++;
                    if(crashCounter > 1){
                        this.endExperiment();
                    }else{
                        ControlUtils.startApp(this.packageName);
                    }
                }
        }
    }

    public void startAliveMonitor(){
        if(this.aliveMonitor != null) {
            this.aliveMonitor.cancel();
        }
        this.aliveMonitor = new Timer();

        this.aliveMonitor.schedule(new TimerTask() {
            @Override
            public void run() {
                if(PluginCommunicator.this.aliveNoResponceCounter > 10){
                    PluginCommunicator.this.onAppNoLongerAlive();
                }else {
                    sendBroadCastToPlugin("CHECK_ALIVE");
                    PluginCommunicator.this.aliveNoResponceCounter += 1;
                }
            }
        }, 0, 1000);
    }

    public void startTimeoutMonitor(){
        if(this.timeoutMonitor != null){
            this.timeoutMonitor.cancel();
        }
        this.timeoutMonitor = new Timer();
        this.timeoutMonitor.schedule(new TimerTask() {
            @Override
            public void run() {
                PluginCommunicator.this.sendBroadCastToPlugin("FINISH_EXPERIMENT");
            }
        }, 1000 * 60 * 2); // 10 Mins


    }


    public void onAppNoLongerAlive(){
        this.aliveMonitor.cancel();
        this.aliveMonitor = null;
        // instead of nicely end the experiment by sending a signal, directly end since the app is no longer alive.
        this.endExperiment();
    }

    public void endExperiment(){
        if(experimentStatus == ExperimentStatus.idle) return;
        this.experimentStatus = ExperimentStatus.idle;
        this.release();
        this.serverCommunicatorWeakReference.get().endExperiment(this.packageName);
    }

    public void log(String string){
        this.serverCommunicatorWeakReference.get().sendLog(string);
        Log.d("Plugin Communicator", string);
    }




    public void sendBroadCastToPlugin(String bcType){
        // START_EXPERIMENT

        Intent intent = new Intent();
        intent.putExtra("bcType", bcType);
        intent.setAction(ENV.intentPatternToPlugin);
        this.activityWeakReference.get().sendBroadcast(intent);

    }



    public void release(){
        ControlUtils.clearTimer(this.timeoutMonitor);
        ControlUtils.clearTimer(this.aliveMonitor);
        this.timeoutMonitor = null;
        this.aliveMonitor= null;
        try{
            this.activityWeakReference.get().unregisterReceiver(this);
        }catch (Exception ignored){}


    }

}
