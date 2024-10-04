package soot.jimple.infoflow.cyfi.result;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import polyglot.lex.Identifier;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.infoflow.cyfi.infoflow.CyfiInfoflowHelper;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DynamicLoadingResultHandler implements ResultsAvailableHandler {

    private static final DynamicLoadingResultHandler instance = new DynamicLoadingResultHandler();

    public static DynamicLoadingResultHandler v(){
        return instance;
    }

    public Set<DCLSourceSinkPath> dynamicLoadingResult;
    public String outputFilePath = "";

    private DynamicLoadingResultHandler(){
        this.dynamicLoadingResult = new HashSet<>();
    }

    @Override
    public void onResultsAvailable(IInfoflowCFG cfg, InfoflowResults results) {
        String entryPointClz = "";
        if(CyfiInfoflowHelper.v().getEntryPointMethod() != null){
            entryPointClz = CyfiInfoflowHelper.v().getEntryPointMethod().getDeclaringClass().getName();
        } else if(CyfiInfoflowHelper.v().getEntryPointClass() != null){
            entryPointClz = CyfiInfoflowHelper.v().getEntryPointClass().getName();
        } else {
            throw new RuntimeException("No entry point specified");
        }

        if(results == null || results.isEmpty()) {
            return;
        }

        for(ResultSinkInfo sinkInfo : results.getResults().keySet()) {
            Stmt stmt = sinkInfo.getStmt();
            if(!stmt.containsInvokeExpr()){
                continue;
            }

            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            SootMethod sinkCalleeMethod =  invokeExpr.getMethod();
            SourceSink sink = null;

            for(ResultSourceInfo sourceInfo : results.getResults().get(sinkInfo)) {
                Stmt sourceStmt = sourceInfo.getStmt();
                // each source should be a method that we defined earlier as a network call

                if(sourceStmt instanceof IdentityStmt){
                    SootMethod sourceMethod = cfg.getMethodOf(sourceStmt);
                    if(sink == null) sink = new SourceSink(sinkCalleeMethod.getSignature(), this.createSinkStackFromPath(sinkCalleeMethod.getSignature(),  sourceInfo, cfg));
                    SourceSink source = new SourceSink(sourceMethod.getSignature(), this.createSourceStackFromPath(sourceMethod.getSignature(), sourceInfo, cfg));
                    this.dynamicLoadingResult.add(new DCLSourceSinkPath(source, sink, entryPointClz, this.getPathResult(sourceInfo, cfg)));
                }else {
                    if(!(sourceStmt.containsInvokeExpr())) continue;
                    InvokeExpr sourceInvokeExpr = sourceStmt.getInvokeExpr();
                    SootMethod sourceCalleeMethod = sourceInvokeExpr.getMethod();

                    if(sink == null) sink = new SourceSink(sinkCalleeMethod.getSignature(), this.createSinkStackFromPath(sinkCalleeMethod.getSignature(), sourceInfo, cfg));
                    SourceSink source = new SourceSink(sourceCalleeMethod.getSignature(), this.createSourceStackFromPath(sourceCalleeMethod.getSignature(), sourceInfo, cfg));
                    this.dynamicLoadingResult.add(new DCLSourceSinkPath(source, sink, entryPointClz, this.getPathResult(sourceInfo, cfg)));
                }

            }
        }
        if(!this.outputFilePath.isEmpty()){
            this.saveResultToFile(this.outputFilePath);
        }
    }



    public void saveResultToFile(String outputFileName){
        try {
            Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
            FileWriter fw = new FileWriter(outputFileName);
            gson.toJson(new ArrayList(this.dynamicLoadingResult), fw);
            fw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    public List<String> createSinkStackFromPath(String sinkMethodSig, ResultSourceInfo resultSourceInfo, IInfoflowCFG icfg){
        if(resultSourceInfo == null || resultSourceInfo.getPath() == null){
            return null;
        }
        Stmt[] path = resultSourceInfo.getPath();
        Stack<String> stack = new Stack<>();
        stack.push(sinkMethodSig);

        for(int i = path.length - 1; i >= 0; i --){
            Stmt stmt = path[i]; // current Stmt
            SootMethod caller = icfg.getMethodOf(stmt);

            if(caller == null) continue;
            if(stack.isEmpty()){
                stack.push(caller.getSignature());
                continue;
            }
            if(caller.getSignature().equals(stack.peek())) continue;
            Set<Unit> currentCalls = icfg.getCallsFromWithin(caller);
            // find if the call is in stack
            for(Unit unit : currentCalls) {
                if (unit instanceof Stmt) {
                    Stmt stmt1 = (Stmt) unit;
                    Collection<SootMethod> callees = icfg.getCalleesOfCallAt(stmt1);
                    List<String> calleeSigs = callees.stream().map(SootMethod::getSignature).collect(Collectors.toList());
                    if(callees != null && calleeSigs.contains(stack.peek())){
                        stack.push(caller.getSignature());
                        break;
                    }
//                    if (stmt1.containsInvokeExpr()) {
//                        InvokeExpr invokeExpr = stmt1.getInvokeExpr();
//                        SootMethod callee = invokeExpr.getMethod();
//                        if (callee.getSignature().equals(stack.peek())) {
//                            stack.push(caller.getSignature());
//                            break;
//                        }
//                    }
                }
            }
        }

        return new ArrayList<>(stack);
    }

    public List<String> getPathResult(ResultSourceInfo resultSourceInfo, IInfoflowCFG cfg){
        if(resultSourceInfo == null || resultSourceInfo.getPath() == null) return null;
        Stmt[] path = resultSourceInfo.getPath();
        List<String> pathResult = new ArrayList<>();

        for(Stmt stmt: path){
            String appendix = "";
            if(stmt instanceof AssignStmt){
                // check if rightStmt is a simply operation with xor

                Value rightOp = ((AssignStmt) stmt).getRightOp();
                if (rightOp instanceof XorExpr){
                    appendix = " !potentialXorDecoding";
                }
            }
            appendix += "!ln" + stmt.getJavaSourceStartLineNumber();
            String lineToAdd = stmt + appendix;
            if((!pathResult.isEmpty()) && pathResult.get(pathResult.size() - 1).equals(lineToAdd)){
                continue;
            }
            pathResult.add(stmt + appendix);
        }
        return pathResult;

    }


    public List<String> createSourceStackFromPath(String sourceMethodSig, ResultSourceInfo resultSourceInfo, IInfoflowCFG cfg){
        if(resultSourceInfo == null || resultSourceInfo.getPath() == null) return null;
        Stmt[] path = resultSourceInfo.getPath();
        Stack<String> stack = new Stack<>();
        stack.push(sourceMethodSig);

        for(Stmt stmt: path){
            SootMethod caller = cfg.getMethodOf(stmt);
            if(caller == null) continue;
            if(stack.isEmpty()){
                stack.push(caller.getSignature());
                continue;
            }
            if(caller.getSignature().equals(stack.peek())) continue;
            Set<Unit> currentCalls = cfg.getCallsFromWithin(caller);
            for(Unit unit: currentCalls){
                if(unit instanceof Stmt){
                    Stmt stmt1 = (Stmt) unit;
                    if(stmt1.containsInvokeExpr()){
                        InvokeExpr invokeExpr = stmt1.getInvokeExpr();
                        SootMethod callee = invokeExpr.getMethod();
                        if(callee.getSignature().equals(stack.peek())){
                            stack.push(caller.getSignature());
                            break;
                        }
                    }
                }
            }
        }

        return new ArrayList<>(stack);
    }



    public static class DCLSourceSinkPath{
        public SourceSink source;
        public SourceSink sink;
        public String entryPointClass;
        public List<String> path;


        public DCLSourceSinkPath(SourceSink source, SourceSink sink, String entryPointClass){
            this.source = source;
            this.sink = sink;
            this.entryPointClass = entryPointClass;
            this.path = new ArrayList<>();
        }

        public DCLSourceSinkPath(SourceSink source, SourceSink sink, String entryPointClass, List<String> path){
            this.source = source;
            this.sink = sink;
            this.entryPointClass = entryPointClass;
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DCLSourceSinkPath that = (DCLSourceSinkPath) o;
            return Objects.equals(source, that.source) && Objects.equals(sink, that.sink) && Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, sink, path);
        }
    }

    public static class SourceSink{
        public String methodName;
        public List<String> stack;

        public SourceSink(String sig, List<String> stack){
            this.methodName = sig;
            this.stack = stack;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SourceSink that = (SourceSink) o;
            return Objects.equals(methodName, that.methodName) && Objects.equals(stack, that.stack);
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodName, stack);
        }
    }


//    public static class DynamicLoadingResult {
//        public List<ResultEntryPoint> resultEntryPoints;
//
//        public DynamicLoadingResult(){
//            this.resultEntryPoints = new ArrayList<>();
//        }
//    }
//
//
//    public static class ResultEntryPoint{
//        public String className;
//        public List<SourceSinkPair> sourceSinkPairs;
//
//        public ResultEntryPoint(String className){
//            this.className = className;
//            this.sourceSinkPairs = new ArrayList<>();
//        }
//
//        public void addSourceSinkPair(SourceSinkPair pair){
//            this.sourceSinkPairs.add(pair);
//        }
//    }
//
//    public static class SourceSinkPair{
//        public List<SourceSink> sources;
//        public SourceSink sink;
//
//        public SourceSinkPair(SourceSink sink, List<SourceSink> sources){
//            this.sources = sources;
//            this.sink = sink;
//        }
//    }

}


