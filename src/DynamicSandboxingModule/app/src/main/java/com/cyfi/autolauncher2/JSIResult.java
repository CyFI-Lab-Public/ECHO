package com.cyfi.autolauncher2;

import java.util.ArrayList;
import java.util.List;

public class JSIResult {
    public String sinkMethod;
    public String url;
    public String interfaceName;
    public String packageName;
    public String entryPointMethod;
    public String entryPointArgs;
    public String sinkArgs;


    public JSIResult(String sinkMethod, String url, String interfaceName, String packageName, String entryPointMethod, String entryPointArgs, String sinkArgs) {
        this.sinkMethod = sinkMethod;
        this.url = url;
        this.interfaceName = interfaceName;
        this.packageName = packageName;
        this.entryPointMethod = entryPointMethod;
        this.entryPointArgs = entryPointArgs;
        this.sinkArgs = sinkArgs;
    }



}
