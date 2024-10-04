package com.cyfi.XposedPlugin.Modules;

import android.content.Context;

import com.cyfi.XposedPlugin.Modules.webviewfuzzing.WebViewFuzzingTestingModule;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ModuleManager {
    static ModuleManager instance = new ModuleManager();

    static public ModuleManager getInstance(){
        return instance;
    }


    private Map<String, Module> modules;
    private WebViewFuzzingTestingModule webviewFuzzingModule;
    private DCLCallStackLoggingModule dclCallStackLoggingModule;
    private DialogDisableModule dialogDisableModule;
    private DCLValidationModule dclValidationModule;

    public void initModules(XC_LoadPackage.LoadPackageParam loadPackageParam){
        modules = new HashMap<>();

        this.webviewFuzzingModule = new WebViewFuzzingTestingModule();
        this.dclCallStackLoggingModule = new DCLCallStackLoggingModule();
        this.dialogDisableModule = new DialogDisableModule();
        this.dclValidationModule = new DCLValidationModule();

        modules.put(WebViewFuzzingTestingModule.class.getName(), webviewFuzzingModule);
        modules.put(DCLCallStackLoggingModule.class.getName(), dclCallStackLoggingModule);
        modules.put(DialogDisableModule.class.getName(), dialogDisableModule);
        modules.put(DCLValidationModule.class.getName(), dclValidationModule);

        for(Module m: modules.values()){
            m.loadModule(loadPackageParam);
        }
    }

    public Module getModule(String moduleName){
        return modules.get(moduleName);
    }

    public void onAttachBaseContext(Context context, int mode){
//        this.dialogDisableModule.onAttachBaseContext(context);
        if(mode == 1 || mode == 4){
            this.dclCallStackLoggingModule.onAttachBaseContext(context);
        }else if (mode == 2){
            this.webviewFuzzingModule.onAttachBaseContext(context);
        }else if(mode == 3){
            this.dclValidationModule.onAttachBaseContext(context);
        }

    }

    public void onApplicationCreate(int mode){
//        this.dialogDisableModule.onApplicationCreate();
        if(mode == 1 || mode == 4){
            this.dclCallStackLoggingModule.onApplicationCreate();
        }else if (mode == 2){
            this.webviewFuzzingModule.onApplicationCreate();
        }else if(mode == 3){
            this.dclValidationModule.onApplicationCreate();
        }
    }
}
