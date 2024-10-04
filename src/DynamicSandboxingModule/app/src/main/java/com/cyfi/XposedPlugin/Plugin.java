package com.cyfi.XposedPlugin;


import android.app.Application;
import android.text.TextUtils;

import com.cyfi.XposedPlugin.Modules.ModuleManager;
import com.cyfi.XposedPlugin.dynamic.DynamicAnalysisManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Plugin implements IXposedHookLoadPackage {
    final DynamicAnalysisManager dynamicAnalysisManager = DynamicAnalysisManager.getInstance();

    public Plugin(){
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if(isInjector(this.getClass().getName())) return;
        String packageName = lpparam.packageName;

        if (XposedUtils.isPackageIgnored(packageName)) {
            return;
        }
        XposedBridge.log("cyfi autolaunche2r2 injector start on " + packageName);




        this.dynamicAnalysisManager.initExperiment(packageName, lpparam);
        XposedBridge.log("=======Package Name:" + lpparam.packageName + "======");
        ModuleManager.getInstance().initModules(lpparam);
    }

    public boolean isInjector(String flag){
        try{
            if(TextUtils.isEmpty(flag)) return false;
            Field methodCacheField = XposedHelpers.class.getDeclaredField("methodCache");
            methodCacheField.setAccessible(true);
            HashMap<String, Method> methodCache = (HashMap<String, Method>) methodCacheField.get(null);
            Method method = XposedHelpers.findMethodBestMatch(Application.class, "onCreate");
            String key = String.format("%s#%s", flag, method.getName());
            if(methodCache.containsKey(key)) return true;
            methodCache.put(key, method);
            return false;
        }catch(Throwable e){
            e.printStackTrace();
        }
        return false;
    }
}
