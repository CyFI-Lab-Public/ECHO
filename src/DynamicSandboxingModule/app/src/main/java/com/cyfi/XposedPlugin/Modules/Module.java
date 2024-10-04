package com.cyfi.XposedPlugin.Modules;


import android.content.Context;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public abstract class Module {
    String moduleName = "";

    public abstract void loadModule(XC_LoadPackage.LoadPackageParam lpparam);

    public abstract void onAttachBaseContext(Context context);

    public abstract void onApplicationCreate();
}
