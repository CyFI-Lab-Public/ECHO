package com.cyfi.XposedPlugin.Modules;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DCLValidationModule extends Module{
    public Context context;


    @Override
    public void loadModule(XC_LoadPackage.LoadPackageParam lpparam) {
    }

    @Override
    public void onAttachBaseContext(Context context) {
        try{
            if(this.context == null) {
                this.context = context;
            }

            hookPrint(context.getClassLoader());
        }catch (Exception e){
            XposedBridge.log(e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onApplicationCreate() {

    }

    public void hookPrint(ClassLoader loader){
        // hook system.out.println method
        XposedBridge.log("DCLValidationModule Hook Print");
        Class androidPrintStream = XposedHelpers.findClass("com.android.internal.os.LoggingPrintStream", loader);

        XposedBridge.hookAllMethods(androidPrintStream, "println", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if(param.args.length > 0 && (param.args[0] instanceof String) && ((String) param.args[0]).startsWith("VALIDATION_RESULT_SEND")) {
                    String output = (String) param.args[0];
                    String[] outputArray = output.split(":");
                    if(outputArray.length == 3){
                        String classname = outputArray[1];
                        String methodname = outputArray[2];
                        String packageName =  DCLValidationModule.this.context.getPackageName();
                        ContentValues cv = new ContentValues();
                        XposedBridge.log("DCLValidationModule " + classname + " " + methodname + " " + packageName);

                        cv.put("classname", classname);
                        cv.put("methodname", methodname);
                        cv.put("packageName", packageName);
                        context.getContentResolver().insert(Uri.parse("content://com.cyfi.autolauncher2.provider/validationresult"), cv);
                        param.setResult(null);
                    }

                }
            }
        });
    }

    public boolean isHookCall(){
        String currentCaller = getCurrentCaller();
        if(currentCaller == null){
            return true;
        }
        if(currentCaller.contains("hookSinkMethods") || currentCaller.contains("beforeHookedMethod") || currentCaller.contains("afterHookedMethod")) return true;

        if(currentCaller.startsWith("com.cyfi") || currentCaller.startsWith("de.robv.android.xposed") ||
                currentCaller.startsWith("androidx.appcompat.widget") || currentCaller.startsWith("com.elderdrivers.riru")){
            return true;
        }


        if(currentCaller.startsWith("java.lang.Class") || currentCaller.startsWith("dalvik.system") ||
                currentCaller.startsWith("java.lang.ClassLoader") || currentCaller.startsWith("external.com.android.dx"))
        {
            return true;
        }


        XposedBridge.log("hook should be processed");
        return false;
    }

    public String getCurrentCaller(){
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean seeHook = false;
        for(StackTraceElement ste: stack){
            if(ste.getClassName().startsWith("EdHooker_") && ste.getMethodName().equals("hook")) {
                seeHook = true;
                continue;
            }
            if(ste.getClassName().startsWith("java.lang.reflect")) continue;

            if(seeHook){
                return ste.getClassName() + "." + ste.getMethodName();
            }
        }
        return null;
    }
}
