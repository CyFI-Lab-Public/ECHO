package com.cyfi.autolauncher2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.cyfi.XposedPlugin.dynamic.control.ControlUtils;
import com.cyfi.controller.serverCommunicator.ServerCommunicator;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import static com.cyfi.XposedPlugin.dynamic.control.ControlUtils.threadSleep;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final ServerCommunicator serverCommunicator = ServerCommunicator.getInstance();

    private TextView infoTextView;
    private TextView packageNameTextView;
    private TextView hashTextView;
    private static String simulatePackage = "com.youku.phone";

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_SETTINGS,
    };

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int readPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);

        int writePermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)  ;
        if (readPermission != PackageManager.PERMISSION_GRANTED || writePermission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            activity.requestPermissions(
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(this);
        keepMute();

        serverCommunicator.bindActivity(this);
        threadSleep(1);
        serverCommunicator.connect();
        infoTextView = findViewById(R.id.infoTV);
        packageNameTextView = findViewById(R.id.packageNameTV);
        hashTextView = findViewById(R.id.hashTV);
        infoTextView.setText(Build.SERIAL);


        this.registerReceiver(new ExternalCommandBroadcastReceiver(), new IntentFilter("externalCmd"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(serverCommunicator != null){
            serverCommunicator.connect();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        serverCommunicator.disconnect();
    }

    public void keepMute() {
        final WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        new Thread() {
            public void run() {
                while (true) {
                    AudioManager amanager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
                    amanager.setStreamMute(AudioManager.STREAM_ALARM, true);
                    amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
                    amanager.setStreamMute(AudioManager.STREAM_RING, true);
                    amanager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
                   System.out.println("K mute!");
                    if (!wifiManager.isWifiEnabled()) {
                        wifiManager.setWifiEnabled(true);
                    }
                    System.out.println("canWrite");
                    threadSleep(30); // 30 seconds
                }
            }
        }.start();
    }


    public void onButtonSimulateStartClick(View view){
        this.serverCommunicator.mode = 1;
//        this.serverCommunicator.startExperiment(simulatePackage, "dynamicloadertest");
        Intent intent = new Intent();
        intent.setAction("externalCmd");
        intent.putExtra("command", "start_experiment");
        intent.putExtra("mode", 1);
        intent.putExtra("pkgName", simulatePackage);
        this.sendBroadcast(intent);
    }


    public void onButtonSimulateMode3Click(View view){
        this.serverCommunicator.mode = 2;
        this.serverCommunicator.startExperiment(simulatePackage, "testhash");
    }

    public void onButtonSimulateFinishClick(View view){
        this.serverCommunicator.endExperiment(simulatePackage);
    }

    public void setTextViewText(final TextView tv, final String s){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv.setText(s);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            Log.v("PERMISSION","Permission: "+permissions[0]+ "was "+grantResults[0]);
            //resume tasks needing this permission
        }else{
            Log.v("PERMISSION","Permission: Failed");
        }
    }
}
