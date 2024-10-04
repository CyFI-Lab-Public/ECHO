package com.cyfi.controller.serverCommunicator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.os.Build;

import com.cyfi.ENV;
import com.cyfi.XposedPlugin.dynamic.flowdroid.FlowDroidResult;
import com.cyfi.XposedPlugin.dynamic.flowdroid.ResolvedMethod;
import com.cyfi.autolauncher2.DCLResult;
import com.cyfi.autolauncher2.DCLValidationResult;
import com.cyfi.autolauncher2.JSIResult;
import com.cyfi.autolauncher2.MainActivity;
import com.cyfi.XposedPlugin.dynamic.control.ControlUtils;
import com.cyfi.controller.fileMonitor.FileMonitor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.intellij.lang.annotations.Flow;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;


public class ServerCommunicator {
//    private WebSocketClient webSocketClient;
    private Socket socket;
    private String deviceName;
    private String sampleHash;

    private String packageName;
    private boolean fileLoggerRegistered = false;
    private PluginCommunicator pluginCommunicator;
    private WeakReference<MainActivity> activityWeakReference;
    private FlowDroidResult flowDroidResult;

    public int mode;

    private String serverUrl;


    // ====================== Methods for Initialization ============================
    @SuppressLint("HardwareIds")
    public ServerCommunicator(){
        try{
            String deviceName = Build.SERIAL;
            FileReader fileReader = new FileReader(ENV.serverUrlFile);
            Type type = new TypeToken<Map<String, String>>(){}.getType();

            Object deserialized = new Gson().fromJson(fileReader, type) ;
            if(deserialized instanceof Map) {
                Map<String, String> map = (Map<String, String>) deserialized;
                if(map != null && map.containsKey(deviceName)){
                    this.serverUrl = map.get(deviceName);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        if(this.serverUrl == null || this.serverUrl.length() == 0){
            if(Arrays.asList(ENV.dclEvaluationList).contains(Build.SERIAL)){
                serverUrl = ENV.dclEvaluationUrl;
            }else{
                serverUrl = ENV.dclValidUrl;
            }
        }

        try {
            initSocket();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void initSocket() throws URISyntaxException {
        this.deviceName = Build.SERIAL;

        IO.Options options = new IO.Options();
        options.reconnection = true;
        options.timeout = 2000000;

        this.socket = IO.socket(new URI(this.serverUrl), options);
        this.socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println("socket get connected");
            }
        });

        this.socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println("socket disconnect");
            }
        });

        this.socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println(args[0].toString());
                if(args[0] instanceof Exception) {
                }
            }
        });

        this.socket.on("message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                System.out.println(args[0]);
                Gson gson = new GsonBuilder().disableHtmlEscaping().create();
                String message = args[0].toString();
                MessageBody messageBody = gson.fromJson(message, MessageBody.class);
                System.out.println(messageBody);
                handleServerMessage(messageBody);
            }
        });

    }

    public void bindActivity(MainActivity activity) {
        this.activityWeakReference = new WeakReference<>(activity);

        if(!this.fileLoggerRegistered){
            try{
                activity.unregisterReceiver(FileLoggingReceiver.getInstance());
            }catch (Exception e){
                e.printStackTrace();
            }
            try{
                activity.registerReceiver(FileLoggingReceiver.getInstance(), new IntentFilter(ENV.intentPatternFileLogging));
            }catch(Exception ignored){
            }

            this.fileLoggerRegistered = true;
        }
    }

 // ==================== Methods of communication  ================================


    public void sendMessage(MessageBody messageBody){
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        String jString = gson.toJson(messageBody);
        this.socket.emit("message", jString);
    }

    public void sendLog(String logContent){
        HashMap<String, String> logs = new HashMap<>();
        logs.put("device", this.deviceName);
        logs.put("content", logContent);
        MessageBody mb = new MessageBody(true, "log", logs);
        this.sendMessage(mb);
    }

    public void connect(){
        this.socket.connect();
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("device_name", this.getDeviceName());
        this.sendMessageToServer(new MessageBody(true, "device_name",  parameters));
    }

    public void disconnect(){
        this.socket.close();
        System.out.println("socket Close Called ");
    }

    // ============================= Methods for Manual Experiment Progress ===========================//
//    public void manualGetAppInstallOnDevice() {
//        String urlString = this.serverUrl + "/api/manual/get_app/" + this.deviceName;
//        this.makeHttpRequest(urlString, null, "POST");
//    }
//
//    public void manualStartApp(){
//        String urlString = this.serverUrl + "/api/manual/start/" + this.deviceName;
//        this.makeHttpRequest(urlString, null, "POST");
//    }
//
//    public void manualFinishExperiment() {
//        String urlString = this.serverUrl + "/api/manual/finish/" + this.deviceName;
//        this.makeHttpRequest(urlString, null, "POST");
//    }
//
//    public void manualInstallDone(String packageName, String sampleHash){
//        this.activityWeakReference.get().installDone(packageName, sampleHash);
//    }



    // ============================= Methods for experiment control ===========================//

    public void startExperiment(String packageName, String sampleHash, int mode) {
        this.mode = mode;
        this.startExperiment(packageName, sampleHash);
    }

    public void startExperiment(String packageName, String sampleHash){
        this.packageName = packageName;
        this.sampleHash = sampleHash;
        this.changeMode(this.mode, packageName);

//        FileMonitor.getInstance().initFileSnaps();

        if(this.pluginCommunicator != null) {
            try{
                this.pluginCommunicator.release();
                this.activityWeakReference.get().unregisterReceiver(pluginCommunicator);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
        this.pluginCommunicator = new PluginCommunicator(this, packageName, this.activityWeakReference.get());
        clearResultTables();
        sendLog("Current Mode: " + String.valueOf(this.mode));
        if(this.mode == 1 || this.mode == 3){
            this.reportNewApp(packageName);
        }
        try{
            this.flowDroidResult = new FlowDroidResult();
            this.flowDroidResult.getEntryPointsFromJson("/data/local/tmp/flowdroid_jsi_" + this.packageName + ".json");
        }catch (FileNotFoundException e){
            this.sendLog("FlowDroid result not found");
        }

        ControlUtils.threadSleep(1);
        startApp(packageName);
    }

    public void endExperiment(String packageName){
        this.stopApp(packageName);
        this.reportAppFinish(packageName);

        this.saveResultFromDB();

        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("package_name", packageName);
        parameters.put("device_name", this.deviceName);
        sendMessage(new MessageBody(true, "job_finish", parameters));
        sendLog("Experiment Finish: " + packageName);

//        clearResultTables();

    }

    public void saveResultFromDB(){
        if(this.mode == 1){
            this.saveDCLResultFromDB();
        }else if(this.mode == 2){
            this.saveJSIResultFromDB();
        }else if(this.mode == 3) {
            this.saveDCLValidationResultFromDB();
        }else if(this.mode == 4){
            this.saveDCLResultFromDB();
        }
    }


    public void installApp(String packageName){
        ControlUtils.installApp(ENV.malwarePath);
        sendLog(String.format("App %s is installed on device %s", packageName, this.deviceName));
    }

    public void uninstallApp(String packageName){
        ControlUtils.uninstallApp(packageName);
        sendLog(String.format("App %s is uninstalled on device %s", packageName, this.deviceName));
    }

    public void startApp(String packageName){
        if(ControlUtils.isAppRunning(this.getActivity(), packageName)){
            ControlUtils.stopApp(packageName);
        }

        ControlUtils.startApp(packageName);
        sendLog(String.format("App %s is started on device %s", packageName, this.deviceName));

    }

    public void stopApp(String packageName){
        ControlUtils.stopApp(packageName);
        sendLog(String.format("App %s is stopped on device %s", packageName, this.deviceName));
    }

    public void monkeyrunApp(String packageName) {
        ControlUtils.monkeyrunApp(packageName);
        sendLog(String.format("App %s is tested with Monkey on device %s", packageName, this.deviceName));
    }

    // ============================= Methods for interaction with proxy ===========================//
    public void makeHttpRequest(final String urlString, final HashMap<String, String> parameters){
        makeHttpRequest(urlString, parameters, "GET");
    }

    public void makeHttpRequest(final String urlString, final HashMap<String, String> parameters, final String requestMethod){
        new Thread(){
            @Override
            public void run() {
                HttpURLConnection connection = null;
                StringBuilder urlBuilder = new StringBuilder(urlString);
                try{
                    if(parameters!= null && parameters.size() > 0){
                        boolean isFirst = true;
                        for(Map.Entry<String, String> entry : parameters.entrySet()){
                            if(isFirst) {
                                urlBuilder.append("?");
                                isFirst = false;
                            } else{
                                urlBuilder.append("&");
                            }
                            urlBuilder.append(String.format("%s=%s", entry.getKey(), entry.getValue()));
                        }
                    }

                    URL url = new URL(urlBuilder.toString());
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setUseCaches(false);
                    connection.getContent();
                    connection.setRequestMethod(requestMethod);
                    Scanner scan = new Scanner(connection.getInputStream());
                    StringBuilder sb = new StringBuilder();
                    while(scan.hasNext()){
                        sb.append(scan.nextLine());
                    }
                    scan.close();
                    Log.d("POST REQUEST", sb.toString());
                }catch (Exception e){
                    e.printStackTrace();
                } finally {
                    if(connection != null) connection.disconnect();
                }
            }
        }.start();
    }

    public void reportNewApp(String packageName){
        // interact
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("package_name", packageName);
        String urlString = ENV.proxyUrl + "/register/" + deviceName + "/" + sampleHash;
        this.makeHttpRequest(urlString, parameters);
        sendLog("Report New App: "+ packageName);
    }

    public void reportAppFinish(String packageName){
//        HashMap<String, String> parameters = new HashMap<>();
//        parameters.put("package_name", packageName);
        String urlString = ENV.proxyUrl + "/unregister/" + deviceName;
        this.makeHttpRequest(urlString, null);
        sendLog("Report Finish App: "+ packageName);

    }

    public void testProxy(){
        String urlString = ENV.proxyUrl + "/check/" + deviceName;
        this.makeHttpRequest(urlString, null);
        sendLog("Test Proxy");
    }

    // ============================= Methods for instance  ===========================//
    private static class ServerCommunicatorInstanceHolder {
        private static final ServerCommunicator instance = new ServerCommunicator();
    }


    public void handleServerMessage(MessageBody messageBody) {
        try{
            String command = messageBody.command;
            HashMap<String, String> parameters = new HashMap<>();
            String packageName;
            String sampleHash;
            Log.i("SocketMessage", messageBody.toString());
            switch (command) {
                case "device_name":
                    parameters.put("device_name", getDeviceName());
                    sendMessageToServer(new MessageBody(true, "device_name",  parameters));
                    break;
                case "app_check":
                    parameters.put("device_name", getDeviceName());
                    sendMessageToServer(new MessageBody(true, "app_check", parameters));
                    break;
                case "mal_running":
                    packageName = messageBody.contents.get("package_name");
                    parameters.put("device_name", getDeviceName());
                    boolean isRunning;
                    try{
                        isRunning = ControlUtils.isAppRunning(getActivity(), packageName);
                    }catch (Exception ignored){
                        isRunning = true;
                    }
                    parameters.put("mal_running", isRunning ? "1": "0");
                    sendMessageToServer(new MessageBody(true, "mal_running", parameters));
                    break;
                case "proxy_check":
                    this.testProxy();
                    break;
                case "start_app":
                    packageName = messageBody.contents.get("package_name");
                    sampleHash = messageBody.contents.get("sample_hash");
                    if(messageBody.contents.containsKey("mode")){
                        String mode = messageBody.contents.get("mode");
                        if(mode != null){
                            try{
                                this.mode = Integer.parseInt(mode);
                            }catch (Exception e){
                                Log.e("Set Mode Exception: ", e.getMessage());
                                this.mode = 1;
                            }
                        }else{
                            this.mode = 1;
                        }
                    }
                    this.startExperiment(packageName, sampleHash);
                    break;
                case "finish_experiment":
                    this.saveResultFromDB();
                    this.reportAppFinish(this.packageName);
                    break;
                // case for manual experiment, ICS specific
//                case "install_done":
//                    packageName = messageBody.contents.get("package_name");
//                    sampleHash = messageBody.contents.get("sample_hash");
//                    this.manualInstallDone(packageName, sampleHash);
//                    break;
//                case "finish_done":
//                    this.manualFinishDone();
//                    break;

                case "disable_wifi":
                    this.disable_wifi();
                    break;
                case "enable_wifi":
                    this.enable_wifi();
                    break;

                default:
                    System.out.println(messageBody);
            }
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println(messageBody);
        }
    }


    public void sendMessageToServer(MessageBody message){
        try{
            this.sendMessage(message);
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void sendFlowDroidResultToPlugin(){
        if(this.flowDroidResult != null){
            flowDroidResult.sendValidResultToExposedPlugin(this.getActivity());
        }
    }




    public static ServerCommunicator getInstance(){
        return ServerCommunicatorInstanceHolder.instance;
    }

    public String getDeviceName(){
        return this.deviceName;
    }


    public String getPackageName() {
        return packageName;
    }

    public Context getActivity(){
        return this.activityWeakReference.get();
    }


    public void saveJSIResultFromDB(){

        List<JSIResult> jsiResultList = new ArrayList<>();

        Cursor cursor = this.getActivity().getContentResolver().query(Uri.parse("content://com.cyfi.autolauncher2.provider/jsiresult"), new String[]{"sinkMethod", "url", "interfaceName", "packageName", "entryPointMethod", "entryPointArgs", "sinkArgs"}, null, null, null);
        if(cursor == null) return;

        while(cursor.moveToNext()){
            try{
                jsiResultList.add(new JSIResult(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4),cursor.getString(5),cursor.getString(6)));
            }catch(Exception ignored){

            }
        }
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String jsonStr = gson.toJson(jsiResultList);
        String outputFileName = this.packageName + "_jsi_result.json";
        sendLog("Save JSI Result to " + outputFileName);

        try{
            ControlUtils.writeSDFile(outputFileName, jsonStr);
        }catch(IOException e){
            sendLog("ERROR: Fail to save file, " + e);
            e.printStackTrace();
        }

    }

    public void saveDCLResultFromDB(){
        List<DCLResult> dclResultList = new ArrayList<>();

        Cursor cursor = this.getActivity().getContentResolver().query(Uri.parse("content://com.cyfi.autolauncher2.provider/dclresult"), new String[]{"method", "packageName", "type", "url", "filePath", "loadClassName", "loadMethodName", "stack", "isStatic", "interfaceNames", "desFilePath"}, null, null, null);

        if(cursor == null) return;
        while(cursor.moveToNext()){
            try{
                if(!cursor.getString(1).equals(this.packageName)){
                    continue;
                }
                dclResultList.add(new DCLResult(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6), cursor.getString(7), cursor.getInt(8) == 1, cursor.getString(9), cursor.getString(10)));
            }catch(Exception ignored){
                System.out.println("DCLResult Exception");
            }
        }

        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String jsonStr = gson.toJson(dclResultList);
        String outputFileName = this.packageName + "_dcl_result.json";
        sendLog("Save DCL Result to " + outputFileName);
        try{
            ControlUtils.writeSDFile(outputFileName, jsonStr);
        }catch(IOException e){
            sendLog("ERROR: Fail to save file, " + e);
            e.printStackTrace();
        }
    }

    public void saveDCLValidationResultFromDB(){
        List<DCLValidationResult> dclValidationResultList = new ArrayList<>();

        Cursor cursor = this.getActivity().getContentResolver().query(Uri.parse("content://com.cyfi.autolauncher2.provider/validationresult"), new String[]{"packageName", "className", "methodName"}, null, null, null);
        if(cursor == null) return;
        while(cursor.moveToNext()){
            try{
                dclValidationResultList.add(new DCLValidationResult(cursor.getString(0), cursor.getString(1), cursor.getString(2)));
            }catch(Exception ignored){

            }
        }


        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String jsonStr = gson.toJson(dclValidationResultList);
        String outputFileName = this.packageName + "_dcl_validation_result.json";
        sendLog("Save DCLValidation  Result to " + outputFileName);

        try{
            ControlUtils.writeSDFile(outputFileName, jsonStr);
        }catch(IOException e){
            sendLog("ERROR: Fail to save file, " + e);
            e.printStackTrace();
        }


    }

    public void clearResultTables(){
        this.getActivity().getContentResolver().delete(Uri.parse("content://com.cyfi.autolauncher2.provider/jsiresult"), null, null);
        this.getActivity().getContentResolver().delete(Uri.parse("content://com.cyfi.autolauncher2.provider/dclresult"), null, null);
        this.getActivity().getContentResolver().delete(Uri.parse("content://com.cyfi.autolauncher2.provider/validationresult"), null, null);

    }

    public void changeMode(int mode, String packageName){
        this.getActivity().getContentResolver().delete(Uri.parse("content://com.cyfi.autolauncher2.provider/mode"), null, null);
        ContentValues contentValues = new ContentValues();
        contentValues.put("mode", mode);
        contentValues.put("packageName", packageName);

        this.getActivity().getContentResolver().insert(Uri.parse("content://com.cyfi.autolauncher2.provider/mode"), contentValues);
    }

    public void disable_wifi(){
        String urlString = ENV.proxyUrl + "/disable_wifi";
        this.makeHttpRequest(urlString, null);
    }

    public void enable_wifi(){
        String urlString = ENV.proxyUrl + "/enable_wifi";
        this.makeHttpRequest(urlString, null);
    }


}
