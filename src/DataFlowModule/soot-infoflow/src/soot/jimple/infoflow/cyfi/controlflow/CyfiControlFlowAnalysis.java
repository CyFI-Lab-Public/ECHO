package soot.jimple.infoflow.cyfi.controlflow;

import java.util.*;

import soot.*;

import soot.jimple.Stmt;
import soot.jimple.infoflow.cyfi.infoflow.CyfiInfoflowHelper;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;

public class CyfiControlFlowAnalysis {
    // given an entry point, check all called of target methods, not considering dataflow


    static public List<SootMethod> getCalledJSIExitMethods(SootMethod entryPoint, IInfoflowCFG icfg){
        return getCalledTargetMethods(entryPoint,getJSIExitTargetMethods(), icfg,  new HashSet<>());
    }


    static public List<String> getJSIExitTargetMethods(){
        String[] list = new String[]{"<android.app.Activity: void onDestroy()>","<android.app.Activity: void finish()>","<android.app.Activity: void finishActivity(int)>","<android.app.Activity: void finishActivityFromChild(android.app.Activity,int)>","<android.app.Activity: void finishAffinity()>","<android.app.Activity: void finishAfterTransition()>","<android.app.Activity: void finishAndRemoveTask()>","<android.app.Activity: void finishFromChild(android.app.Activity)>","<android.app.ActivityManager: void killBackgroundProcesses(java.lang.String)>","<android.app.ActivityManager$AppTask: void finishAndRemoveTask()>","<java.lang.System: void exit(int)>","<java.lang.Process: void destroy()>","<java.lang.Process: java.lang.Process destroyForcibly()>","<android.os.Process: void killProcess(int)>","<java.lang.Runtime: void exit(int)>","<java.lang.Runtime: void halt(int)>","<android.app.Service: void stopSelf()>","<android.app.Service: void stopSelf(int)>","<android.app.Service: boolean stopSelfResult(int)>Thread.destroy()","<java.lang.Thread: void interrupt()>","<java.lang.Thread: void stop()>","<java.lang.Thread: void stop(java.lang.Throwable)>","<java.lang.Thread: void suspend()>"};

        return Arrays.asList(list);
    }



    static public List<SootMethod> getCalledTargetMethods(SootMethod entryPoint, List<String> targetMethods, IInfoflowCFG icfg, HashSet<SootMethod> visited){
        if(entryPoint == null) return new ArrayList<>();
        if(visited.contains(entryPoint)){
            return new ArrayList<>();
        }
        visited.add(entryPoint);

        String sig = entryPoint.getSignature();
//        boolean contains = targetMethods.contains(sig);

        Set<SootMethod> calledMethods = new HashSet<>();

        if(targetMethods.contains(entryPoint.getSignature())){
            calledMethods.add(entryPoint);
            return new ArrayList<>(calledMethods);
        } else if(CyfiInfoflowHelper.v().isClassInAdditionalIgnore(entryPoint.getClass().getName()) || !entryPoint.hasActiveBody()){
            return new ArrayList<>();
        } else  {
            Collection<Unit> callUnits = icfg.getCallsFromWithin(entryPoint);
            if(callUnits == null || callUnits.isEmpty())
                return new ArrayList<>();
            for(Unit callUnit: callUnits){
                if(callUnit instanceof Stmt && ((Stmt) callUnit).containsInvokeExpr()){
                    SootMethod calledMethod = ((Stmt) callUnit).getInvokeExpr().getMethod();
                    List<SootMethod> subSet = getCalledTargetMethods(calledMethod, targetMethods, icfg, visited);
                    calledMethods.addAll(subSet);
                }
            }
        }
        return new ArrayList<>(calledMethods);
    }


}
