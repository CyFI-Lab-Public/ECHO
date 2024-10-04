package com.cyfi.autolauncher2;

public class DCLValidationResult {
    public String methodName;
    public String packageName;
   public String className;

    public DCLValidationResult(String packageName, String className,  String methodName) {
        this.methodName = methodName;
        this.packageName = packageName;
        this.className = className;
    }

}
