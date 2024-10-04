package soot.jimple.infoflow.cyfi.result;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Value;
import soot.jimple.*;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.cyfi.controlflow.CyfiControlFlowAnalysis;
import soot.jimple.infoflow.cyfi.infoflow.CyfiInfoflowHelper;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class JSIReportGenerationResultHandler implements ResultsAvailableHandler {
    
    private static JSIReportGenerationResultHandler instance = new JSIReportGenerationResultHandler();
    
    public static JSIReportGenerationResultHandler v(){
        return instance;
    }

    private JSIReportGenerationResultHandler(){
        this.entryPoints = new HashMap<>();
    }
    
    public Map<String, SourceMethod> entryPoints;

    public InfoflowConfiguration config;
    public String outputFilePath = "";


    private class Arg{
        public String argType;
        public int argIndex;
        public boolean isBase;
        public Set<ArgSource> argSources;

        public Arg(String argType, int argIndex, boolean isBase){
            this.argType = argType;
            this.argIndex = argIndex;
            this.isBase = isBase;
            this.argSources = new HashSet<>();
        }
    }

    private class ArgSource{
        public String sourceType;
        public int sourceIndex;

        public ArgSource(String type, int i){
            this.sourceIndex = i;
            this.sourceType = type;
        }

        @Override
        public int hashCode() {
            int result = new Integer(this.sourceIndex).hashCode();
            result = 31 * result + sourceType.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj){
            if(!(obj instanceof ArgSource)) return false;
            ArgSource as = (ArgSource) obj;
            return (as.sourceIndex == this.sourceIndex && as.sourceType.equals(this.sourceType));
        }

    }

    private class SinkMethod{
        String sinkMethodSignature;
        public boolean isStatic;
        public Arg base;
        public List<Arg> args;

        public SinkMethod(String sig){
            this.sinkMethodSignature = sig;
            this.args = new ArrayList<>();
        }


    }

    private class SourceMethod{
        public String sourceMethodSignature;
        public Map<String, SinkMethod> sinkMethods;
        public Set<String> calledMethods;

        public SourceMethod(String sig){
            this.sourceMethodSignature = sig;
            this.sinkMethods = new HashMap<>();
            this.calledMethods = new HashSet<>();
        }
    }

    @Override
    public void onResultsAvailable(IInfoflowCFG cfg, InfoflowResults results) {
        String entryPointSig = CyfiInfoflowHelper.v().getEntryPointMethod().getSignature();
        SourceMethod jSourceMethod = this.entryPoints.getOrDefault(entryPointSig, new SourceMethod(entryPointSig));
        this.entryPoints.putIfAbsent(entryPointSig, jSourceMethod);


        SootMethod epm = CyfiInfoflowHelper.v().getEntryPointMethod();

        // add called methods
        if(config.getCyfiConfiguration().getLogCallMethod()){
            jSourceMethod.calledMethods.addAll(CyfiInfoflowHelper.v().getCalledAPI().stream().map(SootMethodRef::getSignature).collect(Collectors.toSet()));
        }

        CyfiInfoflowHelper.v().getCalledAPI().clear();


        List<SootMethod> exitSinkMethods = CyfiControlFlowAnalysis.getCalledJSIExitMethods(CyfiInfoflowHelper.v().getEntryPointMethod(), cfg);
        for(SootMethod exitSinkMethod : exitSinkMethods){
            String exitSinkMethodSignature = exitSinkMethod.getSignature();
            SinkMethod jSinkMethod = jSourceMethod.sinkMethods.getOrDefault(exitSinkMethodSignature, new SinkMethod(exitSinkMethodSignature));
            jSourceMethod.sinkMethods.putIfAbsent(exitSinkMethodSignature, jSinkMethod);
        }

        if(results == null || results.isEmpty()) return;
        // all result sink stmt should have a valid invokeExpr as a target sink method. Ignore the rest.
        for (ResultSinkInfo resultSinkInfo : results.getResults().keySet()) {
            Stmt stmt = resultSinkInfo.getStmt();
            if(!stmt.containsInvokeExpr()){
                continue;
            }

            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            SootMethod sinkCalleeMethod =  invokeExpr.getMethod();
            List<Type> argTypes = sinkCalleeMethod.makeRef().getParameterTypes();
            List<Value> args = invokeExpr.getArgs();

            SinkMethod jSinkMethod = jSourceMethod.sinkMethods.getOrDefault(sinkCalleeMethod.getSignature(), null);
            if(jSinkMethod == null){
                jSinkMethod = new SinkMethod(sinkCalleeMethod.getSignature());
                jSourceMethod.sinkMethods.putIfAbsent(sinkCalleeMethod.getSignature(), jSinkMethod);
            }
            Value base = null;

            if(invokeExpr instanceof InstanceInvokeExpr){
                base = ((InstanceInvokeExpr) invokeExpr).getBase();
                if(jSinkMethod.base == null){
                    jSinkMethod.isStatic = false;
                    jSinkMethod.base =new Arg(base.getType().toString(), -1, true);
                }
            } else {
                jSinkMethod.isStatic = true;
                jSinkMethod.base = null;
            }

            if(argTypes.size() > 0 && jSinkMethod.args.size() == 0){
                for(int i = 0; i < argTypes.size(); i ++){
                    jSinkMethod.args.add(new Arg(argTypes.get(i).toString(), i, false));
                }
            }

            AccessPath sinkAccessPath = resultSinkInfo.getAccessPath();

            for (ResultSourceInfo resultSourceInfo : results.getResults().get(resultSinkInfo)) {
                Stmt sourceStmt = resultSourceInfo.getStmt();
                // Under JSI mode, all source should be a identityStmt from the entrypoint method. Ignore the rest.
                if(!(sourceStmt instanceof IdentityStmt)) continue;
                Value rightOp = ((IdentityStmt) sourceStmt).getRightOp();
                //get source info
                ArgSource argSource = null;
                if(!(rightOp instanceof ParameterRef)) continue;
                String type = rightOp.getType().toString();
                int paraIndex = ((ParameterRef) rightOp).getIndex();

                argSource = new ArgSource(type, paraIndex);
                // find the arg to add this argSource
                if(base != null && base.equals(sinkAccessPath.getCompleteValue())){
                  jSinkMethod.base.argSources.add(argSource);
                } else {
                    Value completeValue = sinkAccessPath.getPlainValue();
                    int index = args.indexOf(sinkAccessPath.getPlainValue());
                    if(index != -1){
                        jSinkMethod.args.get(index).argSources.add(argSource);
                    }
                }
            }
        }

        // write step result to file
        if(!outputFilePath.isEmpty()) {
            saveResultToFile(outputFilePath);
        }
    }
    public void saveResultToFile(String outputFileName){
        try {
            Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
            FileWriter fw = new FileWriter(outputFileName);
            if(this.entryPoints == null) this.entryPoints = new HashMap<>();
            gson.toJson(this.entryPoints, fw);
            fw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setConfig(InfoflowConfiguration configuration){
        this.config = configuration;
    }


}
