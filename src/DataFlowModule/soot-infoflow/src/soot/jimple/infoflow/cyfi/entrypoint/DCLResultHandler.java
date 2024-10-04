package soot.jimple.infoflow.cyfi.entrypoint;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.cyfi.infoflow.CyfiInfoflowHelper;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.util.SystemClassHandler;

import java.util.*;
import java.util.stream.Collectors;

public class DCLResultHandler {
    private static final DCLResultHandler instance = new DCLResultHandler();
    private DCLSummaryObject summary;
    private final HashSet<String> interestedMethods;
    private final HashSet<String> fileWriteRelatedMethods;

    private final HashMap<String, HashSet<String>> fileWriteValidSinkCallersMap;

    private final HashMap<String, HashSet<String>> networkValidSinkCallerMap;


    private final HashSet<String> networkRelatedMethods;

    private HashSet<String> potentialEntryPointMethods;
    private HashSet<String> potentialEntryPointClasses;
    private final HashSet<String> entryPointsToProcess;
    private final HashMap<String, Map<String, Set<String>>> threadRunnableCallerCalleeMappingByEntryPointMap;
    private final HashMap<String, Map<String, Set<String>>> threadStartDCallerCalleeMappingByEntryPointMap;
    private final HashMap<String, Map<String, Set<String>>> handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap;
    private final HashMap<String, Map<String, Set<String>>> executorRunnableCallerCalleeMappingByEntryPointMap;

    private HashSet<SootMethod> additionalEntryPoints;
    private DCLResultHandler() {
        this.summary = null;
        this.interestedMethods = new HashSet<>();
        this.fileWriteRelatedMethods = new HashSet<>();
        this.networkRelatedMethods = new HashSet<>();
        this.potentialEntryPointMethods = new HashSet<>();
        this.potentialEntryPointClasses = new HashSet<>();
        this.threadRunnableCallerCalleeMappingByEntryPointMap = new HashMap<>();
        this.threadStartDCallerCalleeMappingByEntryPointMap = new HashMap<>();
        this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap = new HashMap<>();
        this.executorRunnableCallerCalleeMappingByEntryPointMap = new HashMap<>();
        this.entryPointsToProcess = new HashSet<>();
        this.additionalEntryPoints = null;
        this.fileWriteValidSinkCallersMap = new HashMap<>();
        this.networkValidSinkCallerMap = new HashMap<>();

    }

    public static DCLResultHandler v() {
        return instance;
    }

    public void initFromJsonFile(String jsonFilePath){
        this.initSummaryFromJsonFile(jsonFilePath);
        this.initPotentialEntryPointMethodsAndClasses();
    }

    public void initAfterEntrypointMatching(){
        this.initThreadCallerCalleeRelation();
        this.initHandlerCallerCalleeRelation();
        this.initExecutorCallerCalleeRelation();
    }

    public void initSummaryFromJsonFile(String jsonFilePath){
        try{
            summary = DCLSummaryObject.parseJsonFile(jsonFilePath);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void initPotentialEntryPointMethodsAndClasses(){
        if(!this.haveSummary()){
            return;
        }
        for(DCLResult dclResult: summary.network){
            if(dclResult == null || dclResult.method == null ||  dclResult.stack == null || dclResult.stack.size() == 0){
                continue;
            }

            String sourceMethodSig = dclResult.method;
            networkValidSinkCallerMap.putIfAbsent(sourceMethodSig, new HashSet<>());

            boolean findFirstNonSystemMethod = false;
            for(String stackItem: dclResult.stack){
                try {
                    String sig = getSigFromStackItem(stackItem);
                    String className = sig.split(":")[0];

                    if(!findFirstNonSystemMethod && !SystemClassHandler.v().isClassInSystemPackage(className)){
                        findFirstNonSystemMethod = true;
                        networkValidSinkCallerMap.get(sourceMethodSig).add(sig);
                    }
                    interestedMethods.add(sig);
                    networkRelatedMethods.add(sig);
                }catch (Exception e){
                    if(stackItem.startsWith("End of Thread Stack at ")){
                        continue;
                    }
                    e.printStackTrace();
                }
            }
        }

        for(DCLResult dclResult: summary.fileWrite){
            if(dclResult == null || dclResult.method == null ||  dclResult.stack == null || dclResult.stack.size() == 0){
                continue;
            }
            String sinkMethodSig = dclResult.method;
            fileWriteValidSinkCallersMap.putIfAbsent(sinkMethodSig, new HashSet<>());

            boolean findFirstNonSystemMethod = false;
            for(String stackItem: dclResult.stack){
                try {
                    String sig = getSigFromStackItem(stackItem);
                    String className = sig.split(":")[0];
                    if(!findFirstNonSystemMethod && !SystemClassHandler.v().isClassInSystemPackage(className)){
                        findFirstNonSystemMethod = true;
                        fileWriteValidSinkCallersMap.get(sinkMethodSig).add(sig);
                    }
                    interestedMethods.add(sig);
                    fileWriteRelatedMethods.add(sig);
                }catch (Exception e){
                    if(stackItem.startsWith("End of Thread Stack at ")){
                        continue;
                    }
                    e.printStackTrace();
                }
            }
        }
        potentialEntryPointMethods = new HashSet<>(networkRelatedMethods);
        potentialEntryPointMethods.addAll(fileWriteRelatedMethods);
        potentialEntryPointClasses = new HashSet<>();
        for(String sig: potentialEntryPointMethods){
            String className = sig.substring(0, sig.indexOf(":"));
            if(!CyfiInfoflowHelper.v().isClassInAdditionalIgnore(className)) {
                potentialEntryPointClasses.add(className);
            }
        }
        System.out.println("DCLResultHelper potential entrypoint classes and methods Initialized");
    }



    public Set<SootClass> filterEntryPointClassWithDCLResult(Set<SootClass> entryPointClasses){
        if(!this.haveSummary()){
            System.err.println("DCLResultHandler: no summary, return all entryPointClasses");
            return entryPointClasses;
        }

        Set<SootClass> newEntryPoints = new HashSet<>();
        for(SootClass sc: entryPointClasses) {
            if (potentialEntryPointClasses.contains(sc.getName())) {
                newEntryPoints.add(sc);
            }
        }
        entryPointsToProcess.addAll(newEntryPoints.stream().map(SootClass::getName).collect(Collectors.toList()));
        
        for(SootClass sc: newEntryPoints){
            System.out.println("DCLResultHandler: entrypoint class: " + sc.getName());
            for(SootMethod sm: sc.getMethods()){
                if(!sm.hasActiveBody()){
                    try{
                        sm.retrieveActiveBody();
                    }catch (Exception e){
                        continue;
                    }
                }
            }
        }
        return newEntryPoints;
    }

    public void filterSourceStmtWithDCLResult(Map<Unit, Set<Abstraction>> initialSeeds, IInfoflowCFG icfg){
        Set<Unit> keyset = initialSeeds.keySet();
        Set<Unit> keyToRemove = new HashSet<>();

        for(Unit key: keyset){
            SootMethod sm = icfg.getMethodOf(key);
            if(!sm.getDeclaringClass().getName().startsWith("com.iapptracker")){
                keyToRemove.add(key);
            }
        }
        for(Unit key: keyToRemove){
            initialSeeds.remove(key);
        }
    }

    public void filterSinkStmtWithDCLResult(Map<Unit, Set<Abstraction>> sinks){

    }

    public Set<SootMethod> getAdditionalEntrypointMethods(){
        if(this.additionalEntryPoints != null){
            return this.additionalEntryPoints;
        }
        this.additionalEntryPoints = new HashSet<>();
        // put identified runnable / callable methods into entrypoints
        SootClass runnableClz = Scene.v().getSootClass("java.lang.Runnable");
        SootClass callableClz = Scene.v().getSootClass("java.util.concurrent.Callable");
        if(runnableClz == null || callableClz == null){
            return additionalEntryPoints;
        }


        for(String potentialEntryPointMethod: potentialEntryPointMethods){
            if(potentialEntryPointMethod.split(":").length != 2)
                continue;

            try{
                String sootClassName = potentialEntryPointMethod.substring(0, potentialEntryPointMethod.indexOf(":"));
                String sootMethodName = potentialEntryPointMethod.substring(potentialEntryPointMethod.indexOf(":") + 1);
                SootClass sc = Scene.v().getSootClassUnsafe(sootClassName);
                if(sc == null || CyfiInfoflowHelper.v().isClassInAdditionalIgnore(sc.getName())){
                    continue;
                }

                if(sootMethodName.equals("run") && CyfiInfoflowHelper.v().isSubClassOfOrImplemntInterface(sc, runnableClz)){
                    SootMethod runMethod = sc.getMethodUnsafe("void run()");
                    if(runMethod != null){
                        additionalEntryPoints.add(runMethod);
                    }
                }else if(sootMethodName.equals("call") && CyfiInfoflowHelper.v().isSubClassOfOrImplemntInterface(sc, callableClz)){
                    SootMethod callMethod = sc.getMethodUnsafe("java.lang.Object call()");
                    if(callMethod != null){
                        additionalEntryPoints.add(callMethod);
                    }
                }
            }catch(Exception e){
                e.printStackTrace();
            }

        }
        return additionalEntryPoints;
    }

    public Collection<SootMethod> recudeCalleeByMatchingDCLResults(Collection<SootMethod> callees, Unit u, IInfoflowCFG icfg, Abstraction d2) {
        List<SootMethod> newCallees = new ArrayList<>();
        SootClass currentEntryPoint = CyfiInfoflowHelper.v().getEntryPointClass();
        if(currentEntryPoint == null){
            return callees;
        }
        SootMethod caller = icfg.getMethodOf(u);
        if(caller == null){
            return callees;
        }

        // Handle Thread Start callees based on DCL Results.
        if(isCalleeRunnableFromExecutor(u, caller, icfg, d2)){
            Stmt stmt = null;

            do{
                if(d2 == null){
                    break;
                }
                Stmt currentStmt = d2.getCurrentStmt();
                if(currentStmt == null){
                    break;
                }
                if(currentStmt.containsInvokeExpr()){
                    InvokeExpr abstractInvokeExpr = currentStmt.getInvokeExpr();
                    SootMethod abstractCalleeMethod = abstractInvokeExpr.getMethod();

                    SootClass abstractCalleeClass = abstractCalleeMethod.getDeclaringClass();
                    SootClass executorClz = Scene.v().getSootClassUnsafe("java.util.concurrent.Executor");

                    String[] validcalleeNames = {"execute", "submit", "invokeAny", "invokeAll"};
                    Set<String> validCalleeNameSet = new HashSet<String>(Arrays.asList(validcalleeNames));

//        boolean res = CyfiInfoflowHelper.v().isSubClassOfOrImplemntInterface(callerClz, executorClz);

                    if(validCalleeNameSet.contains(abstractCalleeMethod.getName()) && CyfiInfoflowHelper.v().isSubClassOfOrImplemntInterface(abstractCalleeClass, executorClz)){
                        stmt = currentStmt;
                        break;
                    }
                }
                d2 = d2.getPredecessor();
            }while(d2 != null);

            if(d2 == null || stmt == null){
                return callees;
            }
            SootMethod executeCaller = icfg.getMethodOf(stmt);
            if(this.executorRunnableCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()) == null || this.executorRunnableCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()).get(getSigFromSootMethod(executeCaller)) == null){
                return callees;
            }
            Set<String> candidates = this.executorRunnableCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()).get(getSigFromSootMethod(executeCaller));

            for(SootMethod callee: callees){
                if(candidates.contains(getSigFromSootMethod(callee))){
                    newCallees.add(callee);
                }
            }
        } else if(isCalleeThreadStart(u, caller, icfg, d2)){
            if(this.threadStartDCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()) == null || this.threadStartDCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()).get(getSigFromSootMethod(caller)) == null){
                return callees.stream().filter(callee -> getSigFromSootMethod(callee).equals("java.lang.Thread:run")).collect(Collectors.toList());
//                this.threadStartDCallerCalleeMappingByEntryPointMap.put(currentEntryPoint.getName(), new HashMap<>());
            }else{
                Set<String> candidates = this.threadStartDCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()).get(getSigFromSootMethod(caller));
                return callees.stream().filter(callee -> candidates.contains(getSigFromSootMethod(callee))).collect(Collectors.toList());
            }
        }else if(isCalleeRunnableRunFromThreadStart(u, caller, icfg, d2)){
            // first, get thread.start call stmt from d2
            Stmt stmt = null;
            do{
                if(d2 == null){
                    break;
                }
                Stmt currentStmt = d2.getCurrentStmt();
                if(currentStmt == null){
                    break;
                }
                if(currentStmt.containsInvokeExpr()){
                    InvokeExpr abstractInvokeExpr = currentStmt.getInvokeExpr();
                    SootMethod abstractCalleeMethod = abstractInvokeExpr.getMethod();
                    if(abstractCalleeMethod != null && abstractCalleeMethod.getDeclaringClass().getName().equals("java.lang.Thread") && abstractCalleeMethod.getName().equals("start")){
                        stmt = currentStmt;
                        break;
                    }
                }
                d2 = d2.getPredecessor();
            }while(d2 != null);

            if(d2 == null || stmt == null){
                return callees;
            }

            SootMethod threadStartCaller = icfg.getMethodOf(stmt);
            if(this.threadRunnableCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()) == null || this.threadRunnableCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()).get(getSigFromSootMethod(threadStartCaller)) == null){
                return callees;
            }
            Set<String> candidates = this.threadRunnableCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()).get(getSigFromSootMethod(threadStartCaller));

            for(SootMethod callee: callees){
                if(candidates.contains(getSigFromSootMethod(callee))){
                    newCallees.add(callee);
                }
            }
        }else if(isCalleeHandleMessage(u, caller, icfg, d2) || isCalleeRunnableFromHandlerPostOrSendMsg(u, caller, icfg, d2)){
            // get caller from d2
            Stmt stmt = null;
            List<String> handlerSendMessageMethodNames = Arrays.asList("sendMessage", "sendMessageAtFrontOfQueue", "sendMessageAtTime", "sendMessageDelayed", "sendEmptyMessage", "sendEmptyMessageAtTime", "sendEmptyMessageDelayed", "post", "postAtFrontOfQueue", "postAtTime", "postDelayed");
            do {
                if (d2 == null) {
                    break;
                }
                Stmt currentStmt = d2.getCurrentStmt();
                if (currentStmt == null) {
                    break;
                }
                if (currentStmt.containsInvokeExpr()) {
                    InvokeExpr abstractInvokeExpr = currentStmt.getInvokeExpr();
                    SootMethod abstractCalleeMethod = abstractInvokeExpr.getMethod();
                    if (abstractCalleeMethod != null && handlerSendMessageMethodNames.contains(abstractCalleeMethod.getName())) {
                        stmt = currentStmt;
                        break;
                    }
                }
                d2 = d2.getPredecessor();
            }while(d2 != null);

            if(d2 == null || stmt == null){
                return callees;
            }
            SootMethod handlerSendMessageCaller = icfg.getMethodOf(stmt);
            if(this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()) == null || this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()).get(getSigFromSootMethod(handlerSendMessageCaller)) == null){
                return callees;
            }
            Set<String> candidates = this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap.get(currentEntryPoint.getName()).get(getSigFromSootMethod(handlerSendMessageCaller));

//            for(SootMethod callee: callees){
//                if(candidates.contains(getSigFromSootMethod(callee))){
//                    newCallees.add(callee);
//                }
//            }
        }


        if(newCallees.size() == 0){
            return callees;
        }
        return newCallees;
    }


    // Checkers for checking the callee's status based on taint flow method sig
    public void initHandlerCallerCalleeRelation(){
        if(!this.haveSummary()){
            System.err.println("DCLResultHandler: no summary, return all entryPointClasses");
            return;
        }
        List<DCLResult> dclResults = new ArrayList<>();
        dclResults.addAll(summary.network);
        dclResults.addAll(summary.fileWrite);

        for(DCLResult dclResult: dclResults){
            if(dclResult.stack == null || dclResult.stack.size() == 0 ){
                continue;
            }
            List<String> stack = dclResult.stack;
            String entryPointClass = null;

            for(String stackItem: stack) {
                if(stackItem.startsWith("End of Thread Stack at ")){
                    continue;
                }
                String[] stackArray = stackItem.split(":");
                String clz = stackArray[0];
                if(entryPointsToProcess != null && entryPointsToProcess.contains(clz)) {
                    entryPointClass = clz;
                }
            }
            if(entryPointClass == null){
                continue;
            }
            int i = 0;

            while(i < stack.size() - 1){
                while(i < stack.size() - 1 && !stack.get(i).startsWith("End of Thread Stack at ")){
                    i++;
                }
                if(i <= 1 || i >= stack.size() - 2) continue;
                // check if it is a thread case:
                // check if it is between thread.start and thread.run
                if(getSigFromStackItem(stack.get(i + 1)).equals("android.os.Handler:enqueueMessage") && getSigFromStackItem(stack.get(i - 1)).equals("android.os.Handler:dispatchMessage")){
                    // find caller
                    List<String> handlerMethodNames = Arrays.asList("post", "postAtFrontOfQueue", "postAtTime", "postDelayed", "sendMessage", "sendMessageAtFrontOfQueue", "sendMessageAtTime", "sendMessageDelayed", "sendEmptyMessage", "sendEmptyMessageAtTime", "sendEmptyMessageDelayed");
                    List<String>handlerMethodSigs = handlerMethodNames.stream().map(methodName -> "android.os.Handler:" + methodName).collect(Collectors.toList());
                    int idx = i + 1;
                    while(idx < stack.size() - 1 && handlerMethodSigs.contains(getSigFromStackItem(stack.get(idx + 1)))){
                        idx++;
                    }
                    if(idx >= stack.size() - 1) continue;
                    String caller = getSigFromStackItem(stack.get(idx + 1));
                    // find callee
                    idx = i;
                    if(getSigFromStackItem(stack.get(idx - 2)).equals("android.os.Handler:handleCallback") && idx > 2){
                        // the next will be a customized runnable
                        String callee = getSigFromStackItem(stack.get(idx - 3));
                        this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap.putIfAbsent(entryPointClass,new HashMap<>());
                        this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap.get(entryPointClass).putIfAbsent(caller, new HashSet<>());
                        this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap.get(entryPointClass).get(caller).add(callee);
                        // this part helps handle run from a handler's post method
                    }else {
                        String callee = getSigFromStackItem(stack.get(idx - 2)); // it's either handleMessage or a callback's handleCallback
                        this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap.putIfAbsent(entryPointClass,new HashMap<>());
                        this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap.get(entryPointClass).putIfAbsent(caller, new HashSet<>());
                        this.handlerPostOrSendMsgCallerCalleeMappingByEntryPointMap.get(entryPointClass).get(caller).add(callee);
                        // this part helps handle get callees of handleMessage or handleCallback
                    }
                }
                i++;
            }
        }
        System.out.println("finish catch handler caller callee relation");
    }

    public void initExecutorCallerCalleeRelation(){
        if(!this.haveSummary()){
            return;
        }

        List<DCLResult> dclResults = new ArrayList<>();
        dclResults.addAll(summary.network);
        dclResults.addAll(summary.fileWrite);

        for(DCLResult dclResult: dclResults){
            if(dclResult.stack == null || dclResult.stack.size() == 0) {
                continue;
            }

            List<String> stack = dclResult.stack;
            String entryPointClass = null;
            for(String stackItem: stack){
                if(stackItem.startsWith("End of Thread Stack at ")){
                    continue;
                }
                String[] stackArray = stackItem.split(":");
                String clz = stackArray[0];
                if(entryPointsToProcess != null && entryPointsToProcess.contains(clz)) {
                    entryPointClass = clz;
                }
            }
            if(entryPointClass == null){
                continue;
            }

            int i = 0;
            while(i < stack.size() - 1){
                while(i < stack.size() - 1 && !stack.get(i).startsWith("End of Thread Stack at ")){
                    i++;
                }
                if(i <= 1 || i >= stack.size() - 2) {
                    i++;
                    continue;
                }
                // check if it is a thread case:
                // check if it is between thread.start and thread.run
                if(i > 3 && getSigFromStackItem(stack.get(i - 1)).equals("java.lang.Thread:run") && getSigFromStackItem(stack.get(i - 2)).contains("$Worker:run") && getSigFromStackItem(stack.get(i - 3)).contains("runWorker")) {
                    // get caller
                    int idx = i + 2;
                    while (idx < stack.size() - 1 && getSigFromStackItem(stack.get(idx)).startsWith("java.util.concurrent")) {
                        idx++;
                    }
                    if (idx >= stack.size() - 1) {
                        i++;
                        continue;
                    }
                    String caller = getSigFromStackItem(stack.get(idx));
                    // get callee by check first method not in java.util.concurrent
                    idx = i - 4;
                    while (idx >= 0 && getSigFromStackItem(stack.get(idx)).startsWith("java.util.concurrent")) {
                        idx--;
                    }
                    if (idx < 0) {
                        i++;
                        continue;
                    }
                    String callee = getSigFromStackItem(stack.get(idx));
                    //
                    this.executorRunnableCallerCalleeMappingByEntryPointMap.putIfAbsent(entryPointClass, new HashMap<>());
                    this.executorRunnableCallerCalleeMappingByEntryPointMap.get(entryPointClass).putIfAbsent(caller, new HashSet<>());
                    this.executorRunnableCallerCalleeMappingByEntryPointMap.get(entryPointClass).get(caller).add(callee);
                }
                i++;
            }
        }
        System.out.println("finish catch executor caller callee relation");
    }
    public void initThreadCallerCalleeRelation(){
        if(!this.haveSummary()){
            return;
        }

        List<DCLResult> dclResults = new ArrayList<>();
        dclResults.addAll(summary.network);
        dclResults.addAll(summary.fileWrite);

        for(DCLResult dclResult: dclResults){
            if(dclResult.stack == null || dclResult.stack.size() == 0) {
                continue;
            }

            List<String> stack = dclResult.stack;
            String entryPointClass = null;
            for(String stackItem: stack) {
                if(stackItem.startsWith("End of Thread Stack at ")){
                    continue;
                }
                String[] stackArray = stackItem.split(":");
                String clz = stackArray[0];
                if(entryPointsToProcess != null && entryPointsToProcess.contains(clz)) {
                    entryPointClass = clz;
                }
            }
            if(entryPointClass == null){
                continue;
            }
            int i = 0;
            while(i < stack.size() - 1){
                while(i < stack.size() - 1 && !stack.get(i).startsWith("End of Thread Stack at ")){
                    i++;
                }
                if(i <= 1 || i >= stack.size() - 2) continue;
                // check if it is a thread case:
                // check if it is between thread.start and thread.run
                if(getSigFromStackItem(stack.get(i + 1)).equals("java.lang.Thread:start")){
                    String caller = getSigFromStackItem(stack.get(i + 2));
                    if(getSigFromStackItem(stack.get(i - 1)).equals("java.lang.Thread:run")){
                        // is default thread case, we need to handle runnable run with it's caller
                        String callee = getSigFromStackItem(stack.get(i - 2));
                        this.threadRunnableCallerCalleeMappingByEntryPointMap.putIfAbsent(entryPointClass, new HashMap<>());
                        this.threadRunnableCallerCalleeMappingByEntryPointMap.get(entryPointClass).putIfAbsent(caller,new HashSet<>());
                        threadRunnableCallerCalleeMappingByEntryPointMap.get(entryPointClass).get(caller).add(callee);
                    }else{
                        String callee = getSigFromStackItem(stack.get(i - 1));
                        this.threadStartDCallerCalleeMappingByEntryPointMap.putIfAbsent(entryPointClass, new HashMap<>());
                        this.threadStartDCallerCalleeMappingByEntryPointMap.get(entryPointClass).putIfAbsent(caller,new HashSet<>());
                        threadStartDCallerCalleeMappingByEntryPointMap.get(entryPointClass).get(caller).add(callee);
                    }
                }
                i++;
            }
        }
        System.out.println("finish catch thread caller callee relation");
    }

    // check if it is <android.os.Handler:handleMessage> or <android.os.Handler$Callback:handleMessage>

    public boolean isCalleeHandleMessage(Unit u, SootMethod caller, IInfoflowCFG iInfoflowCFG, Abstraction d2){
        SootMethod calleeMethod = getCalleeMethodFromUnit(u, caller, iInfoflowCFG, d2);
        if(calleeMethod == null){
            return false;
        }
        SootClass calleeClass = calleeMethod.getDeclaringClass();
        if((calleeClass.getName().equals("android.os.Handler") || calleeClass.getName().equals("android.os.Handler$Callback")) && calleeMethod.getName().equals("handleMessage")){
            return true;
        }
        return false;
    }

    public boolean isCalleeThreadStart(Unit u, SootMethod caller, IInfoflowCFG iInfoflowCFG, Abstraction d2){
        SootMethod calleeMethod = getCalleeMethodFromUnit(u, caller, iInfoflowCFG, d2);
        if(calleeMethod == null){
            return false;
        }
        if(calleeMethod.getDeclaringClass().getName().equals("java.lang.Thread") && calleeMethod.getName().equals("start")){
            return true;
        }
        return false;
    }

    public boolean isCalleeRunnableRunFromThreadStart(Unit u, SootMethod caller, IInfoflowCFG iInfoflowCFG, Abstraction d2){
        SootMethod calleeMethod = getCalleeMethodFromUnit(u, caller, iInfoflowCFG, d2);
        if(calleeMethod == null){
            return false;
        }
        if(!calleeMethod.getDeclaringClass().getName().equals("java.lang.Runnable") || !calleeMethod.getName().equals("run")){
            return false;
        }
        if(!caller.getDeclaringClass().getName().equals("java.lang.Thread") || !caller.getName().equals("run")){
            return false;
        }
        // we should further check if the runnable is tainted by thread start call. If not, there is no need to check the runnable.run
        do{
            if(d2 == null){
                return false;
            }
            Stmt abstractCurrentStmt = d2.getCurrentStmt();
            if(abstractCurrentStmt == null){
                return false;
            }
            if(abstractCurrentStmt.containsInvokeExpr()){
                InvokeExpr abstractInvokeExpr = abstractCurrentStmt.getInvokeExpr();
                SootMethod abstractCalleeMethod = abstractInvokeExpr.getMethod();
                if(abstractCalleeMethod != null && abstractCalleeMethod.getDeclaringClass().getName().equals("java.lang.Thread") && abstractCalleeMethod.getName().equals("start")){
                    return true;
                }
            }
            d2 = d2.getPredecessor();
        }while(d2 != null);
        return false;
    }

    public boolean isCalleeRunnableFromHandlerPostOrSendMsg(Unit u, SootMethod caller, IInfoflowCFG iInfoflowCFG, Abstraction d2){
        SootMethod calleeMethod = getCalleeMethodFromUnit(u, caller, iInfoflowCFG, d2);
        if(calleeMethod == null){
            return false;
        }
        if(!calleeMethod.getDeclaringClass().getName().equals("java.lang.Runnable") || !calleeMethod.getName().equals("run")){
            return false;
        }
        if(caller.getDeclaringClass().getName().equals("android.os.Handler") && caller.getName().equals("dispatchMessage")){
            return true;
        }
        // we should further check if the runnable is tainted by handler post or send msg call. If not, there is no need to check the runnable.run
        return false;
    }

    public boolean isCalleeRunnableFromExecutor(Unit u, SootMethod caller, IInfoflowCFG iInfoflowCFG, Abstraction d2){
        SootMethod calleeMethod = getCalleeMethodFromUnit(u, caller, iInfoflowCFG, d2);
        if(calleeMethod == null){
            return false;
        }
        if(!calleeMethod.getDeclaringClass().getName().equals("java.lang.Runnable") || !calleeMethod.getName().equals("run")){
            return false;
        }
        SootClass callerClz = caller.getDeclaringClass();
        SootClass executorClz = Scene.v().getSootClassUnsafe("java.util.concurrent.Executor");

        String[] validCallerNames = {"execute", "submit", "invokeAny", "invokeAll"};
        Set<String> validCallerNameSet = new HashSet<String>(Arrays.asList(validCallerNames));

        if(validCallerNameSet.contains(caller.getName()) && CyfiInfoflowHelper.v().isSubClassOfOrImplemntInterface(callerClz, executorClz)){
            return true;
        }else{
            return false;
        }
    }

    public SootMethod getCalleeMethodFromUnit(Unit u, SootMethod caller, IInfoflowCFG icfg, Abstraction d2){
        if(u == null || caller == null || icfg == null || d2 == null){
            return null;
        }
        if(!(u instanceof Stmt) || !((Stmt)u).containsInvokeExpr()){
            return null;
        }
        try {
            InvokeExpr invokeExpr = ((Stmt)u).getInvokeExpr();
            return invokeExpr.getMethod();
        }catch (Exception ignored) {
            return null;
        }
    }

    public boolean haveSummary()
    {
        return summary != null;
    }

    public String getSigFromStackItem(String stackItem){
        String[] stackArray = stackItem.split(":");
        if(stackArray.length != 3){
            throw new RuntimeException("stackString is not in the right format");
        }
        String className = stackArray[0];
        String methodName = stackArray[1];
        return className + ":" + methodName;
    }
    // remove entrypoints based on dcl results, for example, if an ep is not called within any callstack, then it is not considered

    public String getSigFromSootMethod(SootMethod sootMethod){
        return sootMethod.getDeclaringClass().getName() + ":" + sootMethod.getName();
    }
}
