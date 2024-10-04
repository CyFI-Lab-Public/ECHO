package com.cyfi.XposedPlugin.Modules;

import android.app.AndroidAppHelper;
import android.app.FragmentTransaction;

import androidx.fragment.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DialogDisableModule extends Module {

    @Override
    public void loadModule(XC_LoadPackage.LoadPackageParam lpparam) {

    }

    @Override
    public void onAttachBaseContext(Context context) {

    }




    @Override
    public void onApplicationCreate() {
        try{
            disableDialogShow();
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    public void disableDialogShow() throws ClassNotFoundException {
        ClassLoader loader = AndroidAppHelper.currentApplication().getClassLoader();
        Class dialogClass = loader.loadClass("android.app.Dialog");
        XposedHelpers.findAndHookMethod(dialogClass, "show", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("block dialog");
                param.setResult(null);
            }
        });
        Class dialogFragmentClass = loader.loadClass("android.app.DialogFragment");
        XposedHelpers.findAndHookMethod(dialogFragmentClass, "show", FragmentManager.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("block dialog");

                param.setResult(null);
            }
        });

        XposedHelpers.findAndHookMethod(dialogFragmentClass, "show", FragmentTransaction.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("block dialog");

                param.setResult(null);
            }
        });

    }
}
