package soot.jimple.infoflow.cyfi.classmethod;

import soot.SootMethod;

public class LoggingMethod {
    public String methodSignature;
    public String methodHexHash;

    public LoggingMethod(SootMethod sootMethod){
        this.methodSignature = sootMethod.getSignature();
        this.methodHexHash = Integer.toHexString(CyfiClassMethodHelper.methodHashCode(sootMethod));

    }

}
