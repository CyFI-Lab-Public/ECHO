package com.cyfi.XposedPlugin.dynamic;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.app.Application;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.cyfi.ENV;
import com.cyfi.XposedPlugin.Modules.DCLCallStackLoggingModule;
import com.cyfi.XposedPlugin.Modules.Module;
import com.cyfi.XposedPlugin.Modules.ModuleManager;
import com.cyfi.XposedPlugin.Modules.ThreadCallStackHandler;
import com.cyfi.XposedPlugin.Modules.webviewfuzzing.WebViewFuzzingTestingModule;
import com.cyfi.XposedPlugin.dynamic.UIAutoMater.ActionType;
import com.cyfi.XposedPlugin.dynamic.UIAutoMater.UIAction;
import com.cyfi.XposedPlugin.dynamic.UIAutoMater.UIActionDoneInterface;
import com.cyfi.XposedPlugin.dynamic.UIAutoMater.UIActionExecutionQueue;
import com.cyfi.XposedPlugin.dynamic.control.ActivityStateManager;
import com.cyfi.XposedPlugin.dynamic.control.ControlUtils;
import com.cyfi.XposedPlugin.dynamic.flowdroid.FlowDroidResult;


import java.lang.ref.WeakReference;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DynamicAnalysisManager  extends BroadcastReceiver implements Application.ActivityLifecycleCallbacks, UIActionDoneInterface {


    static DynamicAnalysisManager instance = new DynamicAnalysisManager();

    static public DynamicAnalysisManager getInstance(){
        return instance;
    }


    private DynamicAnalysisManager(){
        hasSentAppStartNotification = false;
        killAll = false;
        shouldAllowActivityFinish = false;
        inWebViewJSInterfaceTesting = false;
        pluginBroadcastReceiver = new PluginBroadcastReceiver();
    }

    private String packageName = "";
    private ActivityStateManager activityStateManager;
    private boolean killAll;
    private boolean hasSentAppStartNotification;
    private boolean shouldAllowActivityFinish;
    private boolean inWebViewJSInterfaceTesting;
    private FlowDroidResult flowDroidResult;
    private final PluginBroadcastReceiver pluginBroadcastReceiver;


    public FlowDroidResult getFlowDroidResult() {
        return flowDroidResult;
    }


    public void initExperiment(String packageName, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Exception {
        this.packageName = packageName;
        this.activityStateManager = new ActivityStateManager();
        this.flowDroidResult = new FlowDroidResult();

        hookAttachBaseContext(loadPackageParam);
        hookApplicationOnCreate(loadPackageParam);
        hookDialogShowMethodToDismiss(loadPackageParam);
        hookLifeCycleMethods(loadPackageParam);
        hookFinishMethod(loadPackageParam);


    }

    public void checkAlive(Intent intent){
        //TODO: when receive the intent from the controller, send a response showing that the target app is still alive
        sendBroadCastToController("ALIVE");

    }

    public void startExperiment(){
        XposedBridge.log("start experiment ");
        if(killAll) finishExperiment();
        String currentActivityName = this.activityStateManager.getCurrentActivityName();
        XposedBridge.log("TestLog: current Activity Name" + currentActivityName);
        if(currentActivityName == null) killAll = true;

        this.sendNextAction();
    }

    public void sendNextAction(){
        if(killAll) return;
        UIAction next = this.getNextActionWhenQueueEmpty();
        Thread currentThread = Thread.currentThread();
        XposedBridge.log("TestLog: current Thread " + currentThread.getName());
        if(next == null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    UIAction next = DynamicAnalysisManager.this.getNextActionWhenQueueEmpty();
                    if(next == null){
                        killAll = true;
                        finishExperiment();
                    }else{
                        UIActionExecutionQueue.getInstance().execute(next);
                    }
                }
            }).start();
//            // wait for 5 seconds and check again. if still null, then finish the whole experiment
//            ControlUtils.threadSleep(100);
//            next = this.getNextActionWhenQueueEmpty();
//            if(next == null){
//                killAll = true;
//                finishExperiment();
//                return;
//            }
            return;
        } else{
            if(next.actionType == ActionType.Activity_Finish) shouldAllowActivityFinish = true;
        }
        UIActionExecutionQueue.getInstance().execute(next);
    }

    public UIAction getNextActionWhenQueueEmpty(){
        if(killAll) return null;
        while(!UIActionExecutionQueue.getInstance().isQueueEmpty()){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        Thread.sleep(100);
                    }catch (Exception ignored){
                        ;
                    }
                }
            }).start();
        }
        return this.activityStateManager.getNextAction(this);
    }


    public void finishExperiment(){
        XposedBridge.log("finish experiment " + this.toString());
        sendBroadCastToController("EXPERIMENT_FINISHED");
        ThreadCallStackHandler.getInstance().printThreadStack();
        killAll = true;
    }


    public void onActivityCreate(Activity activity){
        if(!killAll && this.activityStateManager != null) this.activityStateManager.activityOnCreate(activity);
    }

    public void onActivityDestroy(Activity activity){
        if(!killAll && this.activityStateManager!= null) this.activityStateManager.activityOnDestroy(activity);
    }


    public void onActivityResume(Activity activity){
        this.activityStateManager.setCurrentActivityName(activity.toString());
        this.activityStateManager.addOrUpdateActivityState(activity);
        if(! this.hasSentAppStartNotification){
            Application application = activity.getApplication();
            XposedBridge.log("TEST APPLICATION" + application.toString());

            try{
                application.unregisterReceiver(DynamicAnalysisManager.getInstance().pluginBroadcastReceiver);


            } catch(Exception x) {
                XposedBridge.log("Unregister Error" +  x.getLocalizedMessage());
            }
//
            try{
                application.registerReceiver(DynamicAnalysisManager.getInstance().pluginBroadcastReceiver, new IntentFilter(ENV.intentPatternToPlugin));

            }catch (Exception x){
                XposedBridge.log("Register Error" + x.getLocalizedMessage());
            }

            this.hasSentAppStartNotification = true;
            XposedBridge.log("Sending app start notification");
            sendBroadCastToController("APP_STARTED");
        }

    }


    public void hookAttachBaseContext(XC_LoadPackage.LoadPackageParam loadPackageParam){
        XposedHelpers.findAndHookMethod(ContextWrapper.class, "attachBaseContext", Context.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                XposedBridge.log("At Application AttachBaseContext " + param.thisObject.getClass().getName());
                if(!(param.thisObject instanceof Application)) return;

                Context context = (Context) param.args[0];
                packageName = context.getPackageName();
                int mode = getCurrentMode(context);
                if(mode == 0) return;


                XposedBridge.log("Current Mode from attach base context: " + mode + " current PackageName: " + packageName + "caller Type: " + param.thisObject.getClass().getName());
                XposedBridge.log("Current Module Manager: " + ModuleManager.getInstance().toString());
                ModuleManager.getInstance().onAttachBaseContext(context, mode);
            }
        });
    }

    public void hookApplicationOnCreate(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Exception{
        if(killAll) return;
        Class applicationClz = XposedHelpers.findClass("android.app.Application", loadPackageParam.classLoader);

        XposedHelpers.findAndHookMethod(applicationClz, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if(killAll) return;
                Application application = (Application) param.thisObject;
                if(application == null) return;
//
//////
                try{
                    application.unregisterActivityLifecycleCallbacks(DynamicAnalysisManager.this);
                }catch (Exception e){
                    XposedBridge.log(e.getLocalizedMessage());
                }
                AndroidAppHelper.currentApplication().registerActivityLifecycleCallbacks(DynamicAnalysisManager.this);



                XposedBridge.log("Application onCreate");

                setGlobalExceptionHandler();
                int mode = getCurrentMode(application);
                if(mode == 0) return;

                XposedBridge.log("Current Mode from app oncreate: " + mode + " current PackageName: " + packageName + "caller Type: " + param.thisObject.getClass().getName());
                ModuleManager.getInstance().onApplicationCreate(mode);
            }
        });

    }

    public void setGlobalExceptionHandler(){
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try{
                    XposedBridge.log("uncaughtException: " + e.toString());
                    if(DynamicAnalysisManager.this.getInWebViewJSInterfaceTesting()){
                        Module module = ModuleManager.getInstance().getModule(WebViewFuzzingTestingModule.class.getName());
                        if(module != null && (module instanceof WebViewFuzzingTestingModule)){
                            String sig = ((WebViewFuzzingTestingModule) module).currentSourceMethod.sourceMethodSignature;
                            DynamicAnalysisManager.this.sendCrashToController(sig);
                        }
                    } else{
                        DynamicAnalysisManager.this.sendCrashToController("");
                    }
                }catch (Exception e1){

                }

            }
        });
    }


    private void hookFinishMethod(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Exception {
        if(killAll) return;
//        if(true) return;

        Class activityClz = loadPackageParam.classLoader.loadClass("android.app.Activity");

        XposedHelpers.findAndHookMethod(activityClz, "finish", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable{
                super.beforeHookedMethod(param);

                Object obj = param.thisObject;
                if (!(obj instanceof Activity)){
                    XposedBridge.log(":not an valid activity, pass");
                    return;
                }
                final Activity activity = (Activity) obj;




                XposedBridge.log("finish Method, before Hook Method, activity:" + activity);
                if(DynamicAnalysisManager.this.shouldAllowActivityFinish){
                    DynamicAnalysisManager.this.shouldAllowActivityFinish = false;
                    XposedBridge.log("finish allowed");
                }else{
                    XposedBridge.log("finish not allowed");
                    param.setResult(null);
                }
            }

        });
    }


    private void hookLifeCycleMethods(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Exception{
        if(killAll) return;

        //Hook On Create Method:
            // init activity state for the newly created activity
            // push the activity identifier to the activity stack,
        Class activityClz;

        try{
            activityClz = loadPackageParam.classLoader.loadClass("android.app.Activity");
        } catch (ClassNotFoundException classNotFoundException){
            XposedBridge.log("ERROR: necessary lifecycle method <onCreate> not found.");
            killAll = true;
            return;
        }

        try{
            XposedHelpers.findAndHookMethod(activityClz, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Object obj = param.thisObject;
                    if(!(obj instanceof Activity)){
                        XposedBridge.log("ERROR:not an valid activity, pass");
                        return;
                    }
                    DynamicAnalysisManager.this.onActivityCreate((Activity) obj);
                }
            });
        }catch(Exception e){
            XposedBridge.log(e.getLocalizedMessage());
        }


        // when an activity is destroyed, we remove it from the top of the stack
        // if it is not the stack top, we keep pop until the it is out.
        try{
            XposedHelpers.findAndHookMethod(activityClz, "onDestroy", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Object obj = param.thisObject;
                    if(!(obj instanceof Activity)){
                        XposedBridge.log("ERROR:not an valid activity, pass");
                        return;
                    }
                    DynamicAnalysisManager.this.onActivityDestroy((Activity) obj);
                }
            });
        }catch(Exception e){
            XposedBridge.log(e.getLocalizedMessage());
        }

        try{
            XposedHelpers.findAndHookMethod(activityClz, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Object obj = param.thisObject;
                    if (!(obj instanceof Activity)){
                        XposedBridge.log(":not an valid activity, pass");
                        return;
                    }
                    final Activity activity = (Activity) obj;
                    DynamicAnalysisManager.this.onActivityResume(activity);

                }
            });
        }catch(Exception e){
            XposedBridge.log(e.getLocalizedMessage());
        }

    }

    public void hookDialogShowMethodToDismiss(final XC_LoadPackage.LoadPackageParam loadPackageParam){
        try{
            Class dialogClz = loadPackageParam.classLoader.loadClass("android.app.Dialog");
            XposedHelpers.findAndHookMethod(dialogClz, "show", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Object dialogObj = param.thisObject;
                    if(!(dialogObj instanceof Dialog)) return;
                    Dialog dialog = (Dialog) dialogObj;
                    dialog.dismiss();
                }
            });
        }catch(ClassNotFoundException classNotFoundException){
            XposedBridge.log("ERROR: Fail to enable auto dialog dismiss");
            classNotFoundException.printStackTrace();
        }
    }


    // Implement methods for ActivityLifeCycleCallBack
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        XposedBridge.log("TestLog: activity " + activity.toString() + " created");
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        XposedBridge.log("TestLog: activity " + activity.toString() + " destroyed");
    }

    // Implement methods for BroadCaster
    @Override
    public void onReceive(Context context, Intent intent) {
        String bcType = intent.getStringExtra("bcType");
        XposedBridge.log("TestLog: receive broadcast: " + bcType);
        switch(bcType){
            case "CHECK_ALIVE":
                this.checkAlive(intent);
                break;
            case "START_EXPERIMENT":
                this.startExperiment();
                break;
            case "FINISH_EXPERIMENT":
                this.finishExperiment();
                break;
            case "FLOWDROID_RESULT":
                XposedBridge.log("TestLog: receive FLOWDROID_RESULT broadcast");
                this.updateFlowDroidResult(intent);
                break;
            case "TEST":
                break;
        }
    }

    public void sendCrashToController(String currentSig) {
        Intent intent = new Intent();
        intent.putExtra("bcType", "CRASH");
        intent.putExtra("currentSig", currentSig);
        AndroidAppHelper.currentApplication().sendBroadcast(intent);

    }




    public void sendBroadCastToController(String bcType){
        // EXPERIMENT_FINISHED
        // APP_STARTED
        // ALIVE

        Intent intent = new Intent();
        intent.putExtra("bcType", bcType);
        intent.setAction(ENV.intentPatternFromPlugin);
        AndroidAppHelper.currentApplication().sendBroadcast(intent);
    }


    @Override
    public void onActionFinished(UIAction action) {
        sendNextAction();
    }

    private void updateFlowDroidResult(Intent intent) {
        if(intent == null) return;
        String json = intent.getStringExtra("json");
        if(json == null || json.isEmpty()) return;
        if(this.flowDroidResult == null){
            flowDroidResult = new FlowDroidResult();
        }
        flowDroidResult.updateEntryPointFromJsonString(json);
        XposedBridge.log("total flowdroid results: " + flowDroidResult.entryPoints.size());
    }





    public void setInWebViewJSInterfaceTesting(boolean inWebViewJSInterfaceTesting){
        this.inWebViewJSInterfaceTesting = inWebViewJSInterfaceTesting;
    }

    public boolean getInWebViewJSInterfaceTesting(){
        return this.inWebViewJSInterfaceTesting;
    }


    public int getCurrentMode(Context context){
        Cursor cursor = context.getContentResolver().query(Uri.parse("content://com.cyfi.autolauncher2.provider/mode"), new String[]{"mode", "packageName"} , null, null, null);
        while(cursor.moveToNext()){
            String packageName = cursor.getString(cursor.getColumnIndex("packageName"));
            if(packageName == null) continue;
            if(packageName.equals(AndroidAppHelper.currentPackageName())){
                int mode = cursor.getInt(cursor.getColumnIndex("mode"));
                return mode;
            }
        }
        return 0;
    }

    private static class  PluginBroadcastReceiver extends BroadcastReceiver{
        public PluginBroadcastReceiver(){
        }
        @Override
        public void onReceive(Context context, Intent intent) {
           DynamicAnalysisManager.getInstance().onReceive(context, intent);
        }
    }
}
