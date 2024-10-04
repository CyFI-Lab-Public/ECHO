package soot.jimple.infoflow.cyfi.classmethod;

import soot.SootClass;
import soot.SootField;
import soot.SootMethod;

import java.util.ArrayList;
import java.util.List;

public class LoggingClass {
    public String className;
    public List<LoggingMethod> methods;
    public List<String> fields;
    public String classHexHash;


    public LoggingClass(SootClass sootClass){
        this.className = sootClass.getName();
        this.classHexHash = Integer.toHexString(CyfiClassMethodHelper.classHashCode(sootClass));
        this.methods = new ArrayList<>();
        for(SootMethod m: sootClass.getMethods()){
            this.methods.add(new LoggingMethod(m));
        }

        this.fields = new ArrayList<>();
        for(SootField f: sootClass.getFields()){
            this.fields.add(f.getSignature());
        }
    }

    public boolean isEmptyClz(){
        return this.methods.isEmpty() && this.fields.isEmpty();
    }
}
