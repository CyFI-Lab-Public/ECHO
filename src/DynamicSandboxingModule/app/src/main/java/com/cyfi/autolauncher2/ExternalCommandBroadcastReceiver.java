package com.cyfi.autolauncher2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.cyfi.controller.serverCommunicator.ServerCommunicator;


public class ExternalCommandBroadcastReceiver extends BroadcastReceiver {
    public static String actionName = "externalCmd";

    public ExternalCommandBroadcastReceiver() {
        Log.d("ExternalCommandBroadcastReceiver", "ExternalCommandBroadcastReceiver: init");
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(!actionName.equals(action)){
            return;
        }
        String command = intent.getStringExtra("command");
        if(command.equals("start_experiment")){
            try{
                String pkgName = intent.getStringExtra("pkgName");
                String sampleHash = intent.getStringExtra("sampleHash");
                Integer mode = intent.getIntExtra("mode", 0);
                Toast.makeText(context, "Start experiment for " + pkgName, Toast.LENGTH_SHORT).show();
                ServerCommunicator sc = ServerCommunicator.getInstance();
                if(sc != null && sc.getActivity() != null){
                    sc.startExperiment(pkgName, sampleHash, mode);
                }

            }catch (Exception e){
                Log.e("ExternalCommandBroadcastReceiver Error", "onReceive: " + e.toString());
                e.printStackTrace();
            }

        }
    }
}
