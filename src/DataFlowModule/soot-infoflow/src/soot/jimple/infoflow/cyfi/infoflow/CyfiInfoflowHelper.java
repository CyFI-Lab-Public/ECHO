package soot.jimple.infoflow.cyfi.infoflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowManager;
import soot.jimple.infoflow.cfg.FlowDroidEssentialMethodTag;
import soot.jimple.infoflow.cyfi.entrypoint.DCLResultHandler;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.problems.InfoflowProblem;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.sourcesSinks.definitions.AccessPathTuple;
import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkType;
import soot.jimple.infoflow.sourcesSinks.definitions.StatementSourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.manager.BaseSourceSinkManager;
import soot.jimple.infoflow.sourcesSinks.manager.ISourceSinkManager;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.util.MultiMap;
import soot.util.SingletonList;

import java.util.*;
import java.util.stream.Collectors;

// The  class is used to implement methods for modifying the infoflow based on our requirement
public class CyfiInfoflowHelper{
    static private CyfiInfoflowHelper instance;

    static {
        instance = new CyfiInfoflowHelper();
    }

    public static CyfiInfoflowHelper v(){
        return instance;
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private InfoflowProblem infoflowProblem;
    private SootMethod entryPointMethod;
    private SootClass entryPointClass;

    public  Map<String, List<TargetMethodnArg>> backtraceTarget = new HashMap<>();
    public Set<SootMethodRef> calledAPI = new HashSet<>();

    public void addMethodToBackTrackingMap(String keyMethod, TargetMethodnArg targetMethodnArg){
        List<TargetMethodnArg> targetList = backtraceTarget.get(keyMethod);
        if(targetList == null){
            targetList = new ArrayList<>();
            backtraceTarget.put(keyMethod, targetList);
        }
        if(!targetList.contains(targetMethodnArg)) targetList.add(targetMethodnArg);
    }


    private CyfiInfoflowHelper(){
        // init backtraceTarget
//        initBackTraceTargetMap();
    }


    public void patchStatementSinkSourceDefinition(ISourceSinkManager iSourceSinkManager, InfoflowManager infoflowManager) {
        if (iSourceSinkManager instanceof BaseSourceSinkManager) {
            BaseSourceSinkManager baseSourceSinkManager = (BaseSourceSinkManager) iSourceSinkManager;
            MultiMap<String, ISourceSinkDefinition> sourceDefs = baseSourceSinkManager.getSourceDefs();
            Set<ISourceSinkDefinition> globalSourceDefs = sourceDefs.get(baseSourceSinkManager.GLOBAL_SIG);
            for (ISourceSinkDefinition iDef : globalSourceDefs) {
                if (iDef instanceof StatementSourceSinkDefinition && ((StatementSourceSinkDefinition) iDef).getAccessPaths().isEmpty()) {
                    StatementSourceSinkDefinition ssDef = (StatementSourceSinkDefinition) iDef;
                    ssDef.getAccessPaths().add(AccessPathTuple.fromPathElements(ssDef.getLocal().getType().toString(), new ArrayList<>(), new ArrayList<>(), SourceSinkType.Source));
                }
            }
        }
        //TODO: patch statement sink definition if needed

    }
    public void initBackTraceTargetMap(){
        // if we see a "key" method, then we will find any invoke of the target class: method and make the "arg"'s actual Method to Run as the callee.
        // if the arg is null, then we make the object's actual Method to run as the callee.

        SootClass threadClz = Scene.v().getSootClassUnsafe("java.lang.Thread");
        SootClass handlerClz = Scene.v().getSootClassUnsafe("android.os.Handler");
        SootClass runnableClz = Scene.v().getSootClassUnsafe("java.lang.Runnable");

        List<TargetMethodnArg> runnableRunTarget = new ArrayList<>();
        backtraceTarget.put("<java.lang.Runnable: void run()>", runnableRunTarget);

        runnableRunTarget.add(new TargetMethodnArg(threadClz, "<init>", "java.lang.Runnable", "void run()"));
        runnableRunTarget.add(new TargetMethodnArg(handlerClz, "post", "java.lang.Runnable", "void run()"));
        runnableRunTarget.add(new TargetMethodnArg(handlerClz,"postAtFrontOfQueue", "java.lang.Runnable", "void run()"));
        runnableRunTarget.add(new TargetMethodnArg(handlerClz,"postAtTime", "java.lang.Runnable", "void run()"));
        runnableRunTarget.add(new TargetMethodnArg(handlerClz,"postDelayed", "java.lang.Runnable", "void run()"));

        List<TargetMethodnArg> handlerHandleMessageTarget = new ArrayList<>();
        backtraceTarget.put("<android.os.Handler: void handleMessage(android.os.Message)>", handlerHandleMessageTarget);
        handlerHandleMessageTarget.add(new TargetMethodnArg( handlerClz ,"<init>", null, "void handleMessage(android.os.Message)"));
        //TOO: add more clz to track
    }





    public Collection<SootMethod> getCalleesOf(Unit u, IInfoflowCFG icfg, Abstraction d2){
        Collection<SootMethod> callees = icfg.getCalleesOfCallAt(u);
        if(callees.isEmpty()){
            if(u instanceof Stmt) {
                Stmt stmt = (Stmt) u;
                if (stmt.containsInvokeExpr()) {
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    SootMethod sm = invokeExpr.getMethod();
                    if (sm != null) {
                        callees = Collections.singletonList(sm);
                    }
                }
            }
        }

        if (callees.size() <= 1)
            return callees;
        else {
            // Firstly, we need to go back to track whether the actual implementing class is in taint data
            callees = DCLResultHandler.v().recudeCalleeByMatchingDCLResults(callees, u, icfg, d2);
//          if(callees.size() > 1){
            callees = reduceCalleeByBackTracking(callees, u, icfg, d2);
//            // Secondly, for not satisfied results, reduce number by getting inner classes within the interface package
//            callees = reduceCalleesBySearchingAnonymousClass(callees, u, icfg, d2);
//            // Thirdly, check if any class uncer the same pacakge name
//            callees = reduceCalleessByPackageNameSearching(callees, u, icfg, d2);
            // last: remove all system / support classes
            callees = callees.stream().filter(this::isMethodAQualifiedCallee).collect(Collectors.toList());
//            if(callees.size() >= 2){
////                System.out.println(u);
//            }
            return callees;
        }
   }



    public Collection<SootMethod> reduceCalleesBySearchingAnonymousClass(Collection<SootMethod> callees, Unit u, IInfoflowCFG icfg, Abstraction d2) {
        if (callees.size() <= 1 || !((Stmt) u).containsInvokeExpr()) return  callees;

        String epClzName = "";
        if(this.entryPointClass != null) {
            epClzName = this.entryPointClass.getName();
        } else if(this.entryPointMethod != null){
            epClzName = this.entryPointMethod.getDeclaringClass().getName();
        }
        if(epClzName.equals("")) return callees;

        List<SootMethod> tmpResults = new ArrayList<>();
        for(SootMethod callee: callees){
            SootClass clz = callee.getDeclaringClass();
            while(clz != null && clz.isInnerClass()){
                if(clz.getOuterClass().getName().equals(epClzName)) {
                   tmpResults.add(callee);
                    break;
                }
                clz = clz.getOuterClass();
            }
        }
        if(tmpResults.size() > 0) return tmpResults;
        else return callees;
    }

    public Collection<SootMethod> reduceCalleessByPackageNameSearching(Collection<SootMethod> callees, Unit u, IInfoflowCFG icfg, Abstraction d2) {
        if (callees.size() <= 1 || !((Stmt) u).containsInvokeExpr()) return  callees;

        String epClzName = "";
        if(this.entryPointClass != null) {
            epClzName = this.entryPointClass.getName();
        } else if(this.entryPointMethod != null){
            epClzName = this.entryPointMethod.getDeclaringClass().getName();
        }
        if(epClzName.equals("")) return callees;

        final String finalEpClzName = epClzName;
        List<SootMethod> tmpResults = callees
                .stream()
                .filter(m -> (m.getDeclaringClass().getPackageName().equals(finalEpClzName) && m.getDeclaringClass().isInnerClass()))
                .collect(Collectors.toList());
        if(tmpResults.size() > 0) return tmpResults;
        else return callees;

    }

    public Collection<SootMethod> reduceCalleeByBackTracking(Collection<SootMethod> callees, Unit u, IInfoflowCFG icfg, Abstraction d2) {
        // This method only works for certain implementation. We handle the classes case by case
//        if (callees.size() <= 1 || !((Stmt) u).containsInvokeExpr()) return  callees;
        if(d2 == null) return callees;

        List<SootMethod> tmpResults = new ArrayList<>();
        InvokeExpr invokeExpr = ((Stmt) u).getInvokeExpr();
        if(invokeExpr == null) return callees;
//
//        SootMethod callerMethod = icfg.getMethodOf(u);
//        SootClass callerClass = callerMethod.getDeclaringClass();
//
        SootMethod calleeMethod = invokeExpr.getMethod();
//        SootClass calleeClass = calleeMethod.getDeclaringClass();
        if(calleeMethod == null) return callees;

        String calleeSig = calleeMethod.getSignature();

        if (backtraceTarget.containsKey(calleeSig)) {
            List<TargetMethodnArg> targetMethodnArgs = backtraceTarget.get(calleeSig);
            Abstraction d = d2;
            SootMethod match;
            do {
                match = getValidBackTrackingTarget(d, targetMethodnArgs);
                d = d.getPredecessor();
            } while (d != null && match == null);

            if (d != null) {
//                d.getCurrentStmt().getInvokeExpr().getMethod();
                tmpResults.add(match);
            }
        }
        if (tmpResults.size() > 0) return tmpResults;
        else return callees;
    }

    public SootMethod getValidBackTrackingTarget(Abstraction d, List<TargetMethodnArg> targetMethodnArgs) {
        if (d == null) return null;
        if (d.getCurrentStmt() == null) return null;
        else {
            Stmt stmt = d.getCurrentStmt();
            if(!stmt.containsInvokeExpr()) return null;
            InvokeExpr invokeExpr = stmt.getInvokeExpr();

            SootMethod invokeMethod = invokeExpr.getMethod();
            SootClass invokeClz = invokeMethod.getDeclaringClass();


            List<String> argTypes = invokeMethod.getParameterTypes().stream()
                    .map(t -> t.toString()).collect(Collectors.toList());

            SootMethod match = null;
            for (TargetMethodnArg targetMethodnArg : targetMethodnArgs) {
                try {
                    if (this.isSubClassOfOrImplemntInterface(invokeClz, targetMethodnArg.classOrSuperClass) // check if the stmt is a valid target method => class match, method name match, and arg match
                            && targetMethodnArg.methodName.equals(invokeMethod.getName())
                            && (targetMethodnArg.argTypeStr == null || argTypes.contains(targetMethodnArg.argTypeStr))) {


                        Type targetClzType = null;
                        if (targetMethodnArg.argTypeStr != null) {
                            int argIndex = argTypes.indexOf(targetMethodnArg.argTypeStr);
                            Value arg = invokeExpr.getArg(argIndex);
                            targetClzType = arg.getType();
                        } else { // arg is null, we need to find the object of the caller, and find the target method from the caller's class
                            if(invokeExpr instanceof InstanceInvokeExpr){
                                Value v = ((VirtualInvokeExpr) invokeExpr).getBase();
                                targetClzType = v.getType();
                            }
                        }

                        if (targetClzType == null || SystemClassHandler.v().isClassInSystemPackage(targetClzType) || isClassInSupportPackage(targetClzType)) {
                            continue;
                        }
                        SootClass actualTargetClz = Scene.v().getSootClassUnsafe(targetClzType.toString());
                        SootMethod actualTargetMethod = actualTargetClz.getMethodUnsafe(targetMethodnArg.actualMethodToRun);
                        if(actualTargetMethod != null) {
                            match = actualTargetMethod;
                            break;
                        }
                    }
                } catch (Exception exception) {
                    logger.debug(exception.toString());
                }
            }
            return match;
        }
    }

    public Collection<SootMethod> reduceCalleeByMatchingParameter(Collection<SootMethod> callees, Unit u, IInfoflowCFG icfg, Abstraction d2){
        if(!(u instanceof Stmt)) return callees;
        Stmt stmt = (Stmt) u;
        if(!stmt.containsInvokeExpr()) return new ArrayList<>();
        List<SootMethod> res = new ArrayList<>();

        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        SootMethod invokeMethod = invokeExpr.getMethod();
        List<Type> parameterTypes =  invokeMethod.getParameterTypes();
        for(SootMethod callee: callees){
            if(!callee.getName().equals(invokeMethod.getName())) continue;
            List<Type> calleeParameterTypes = callee.getParameterTypes();
            if(parameterTypes.size() != calleeParameterTypes.size()) continue;
            boolean match = true;
            for(int i = 0; i < parameterTypes.size(); i++){
                if(!parameterTypes.get(i).equals(calleeParameterTypes.get(i))){
                    match = false;
                    break;
                }
            }

            if(match) res.add(callee);
        }
        return res;
    }


    public static class TargetMethodnArg {
        public SootClass classOrSuperClass = null;
        public String methodName = "";
        public String argTypeStr = "";
        public String actualMethodToRun = "";

        public TargetMethodnArg(SootClass classOrSuperClass, String methodName, String argTypeStr, String actualMethodToRun) {
            this.classOrSuperClass = classOrSuperClass;
            this.methodName = methodName;
            this.argTypeStr = argTypeStr;
            this.actualMethodToRun = actualMethodToRun;
        }

        @Override
        public boolean equals(Object obj){
            if(!(obj instanceof TargetMethodnArg)) return false;
            else {
                TargetMethodnArg b2 = (TargetMethodnArg) obj;
                 return (this.methodName.equals(b2.methodName)
                        && this.argTypeStr.equals(b2.argTypeStr)
                        && this.actualMethodToRun.equals(b2.actualMethodToRun)
                        && this.classOrSuperClass.equals(b2.classOrSuperClass));
            }
        }
    }


    public boolean isSubClassOfOrImplemntInterface(SootClass candidate, SootClass clzToCheck){
        if(clzToCheck == null || candidate == null) return false;
        else if(candidate == clzToCheck) return true;
        else if(clzToCheck.isInterface()){
            return candidate.isInterface() ? isClassASubClassOf(candidate, clzToCheck) : isClassImplementInterface(candidate, clzToCheck);
        } else if(candidate.isInterface()) { //candidate is an interface, while clzToCheck is a real class
            return false;
        } else { // both are classes
            return isClassASubClassOf(candidate, clzToCheck);
        }
    }

    public boolean isClassImplementInterface(SootClass candidate, SootClass interfaceToCheck){
        if(interfaceToCheck == null || candidate == null) return false;
        if(candidate.isInterface()){
            if(candidate.getInterfaces().contains(interfaceToCheck)) return true;
            else {
                for(SootClass interfaceClz : candidate.getInterfaces()){
                    if(isClassImplementInterface(interfaceClz, interfaceToCheck)) return true;
                }
                return false;
            }
        }

        do {
            for(SootClass i: candidate.getInterfaces()){
                if(i == interfaceToCheck || this.isClassASubClassOf(i, interfaceToCheck)) return true;
            }
            candidate = candidate.getSuperclass();
        }while(candidate != null &&  candidate.hasSuperclass());
        return false;
    }

    public boolean isClassASubClassOf(SootClass candidate,SootClass superClzToCheck){
        if(superClzToCheck == null || candidate == null) return false;
        if(candidate.isInterface() && superClzToCheck.isInterface()){
            return isClassImplementInterface(candidate, superClzToCheck);
        }

        while (candidate != null && candidate.hasSuperclass()){
            if(candidate.getSuperclass().equals(superClzToCheck)) return true;
            candidate = candidate.getSuperclass();
        }
        return false;
    }


    public boolean isClassInSupportPackage(Type type){
        return isClassInSupportPackage(((RefType) type).getSootClass().getName());
    }
    public boolean isClassInSupportPackage(SootClass sootClass){
        return isClassInSupportPackage(sootClass.getName());
    }

    public boolean isClassInSupportPackage(String className){
        return (className.startsWith("androidx.") || className.startsWith("android.support") || className.startsWith("java.util") || className.startsWith("java")
        ||(className.startsWith("org.w3c.dom.") || className.startsWith("kotlinx.coroutines")));
    }

    public boolean isClassInAdditionalIgnore(String className){
        String[] ignoredClasses = new String[]{"int", "byte", "char", "double", "float", "short", "long", "void", "boolean"};

        String[] ignoredPrefixes = new String[]{"android.", "java.", "javax.", "sun.", "org.omg.", "org.w3c.dom.", "com.android.", "androidx.", "android.support.", "java.util.", "dalvik.", "libcore.",
                "org.apache.", "org.xml.", "org.ccil.", "org.json.", "org.xmlpull.", "com.sun.", "org.kxml2.io.", "junit.framework.Assert.",
                "com.elderdrivers.riru", "de.robv.android.xposed", "external.com.android.dx", "kotlin.", "com.google.android.material.", "kotlinx."};
        for(String clz: ignoredClasses){
            if(className.equals(clz)) return true;
        }
        for(String prefix: ignoredPrefixes){
            if(className.startsWith(prefix)){
                return true;
            }
        }
        return false;
    }


    public boolean isMethodAQualifiedCallee(SootMethod sootMethod){
        if(!sootMethod.isConcrete()) return false; // if no body, skip
        else if(sootMethod.hasTag(new FlowDroidEssentialMethodTag().getName())) return true;// for sure process essential method
        else if(SystemClassHandler.v().isClassInSystemPackage(sootMethod.getDeclaringClass().getType())) return false; // exclude all system class
        else if(isClassInSupportPackage(sootMethod.getClass().getName())) return false; // exclude support class method
        else if(isClassInAdditionalIgnore(sootMethod.getClass().getName())) return false;
        else return true;
    }


    public SootMethod getEntryPointMethod() {
        return entryPointMethod;
    }

    public void setEntryPointMethod(SootMethod entryPointMethod){
        this.entryPointMethod = entryPointMethod;
    }

    public void setInfoflowProblem(InfoflowProblem infoflowProblem) {
        this.infoflowProblem = infoflowProblem;
    }

    public InfoflowProblem getInfoflowProblem() {
        return infoflowProblem;
    }


    public void resetCalledAPI(){
        if(calledAPI == null) this.calledAPI = new HashSet<>();
        else calledAPI.clear();
    }

    public void addNewCalledAPI(SootMethod sm){
        if(calledAPI != null && sm != null) this.addNewCalledAPI(sm.makeRef());
    }

    public void addNewCalledAPI(SootMethodRef smr){
        if(calledAPI != null &&smr != null) calledAPI.add(smr);
    }

    public void addNewCalledAPI(String sig){
        if(calledAPI == null ||sig == null) return;
        try{
            SootMethod sm = Scene.v().getMethod(sig);
            this.addNewCalledAPI(sm);
        }catch (Exception e){
            logger.debug("ERROR: find a called method but failed to see actual method from Soot");
        }
    }

    public Set<SootMethodRef> getCalledAPI(){
        return this.calledAPI;
    }

    public SootClass getEntryPointClass() {
        return entryPointClass;
    }

    public void setEntryPointClass(SootClass entryPointClass) {
        this.entryPointClass = entryPointClass;
    }



}
