package com.cyfi.controller.serverCommunicator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cyfi.XposedPlugin.dynamic.control.ControlUtils;

import java.io.IOException;

public class FileLoggingReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("Receive File Write", intent.getStringExtra("path"));
        String path = intent.getStringExtra("path");
        String content = intent.getStringExtra("content");
        try {
            ControlUtils.writeSDFile(path, content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static class FileLoggingReceiverInstanceHolder {
        private static final FileLoggingReceiver instance = new FileLoggingReceiver();
    }

    public static FileLoggingReceiver getInstance(){
        return FileLoggingReceiver.FileLoggingReceiverInstanceHolder.instance;
    }
}
