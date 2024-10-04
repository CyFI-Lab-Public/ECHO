package soot.jimple.infoflow.cyfi.sourceNsink;

import soot.*;
import soot.jimple.IdentityStmt;
import soot.jimple.ParameterRef;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.cyfi.infoflow.CyfiInfoflowHelper;
import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.definitions.ISourceSinkDefinitionProvider;
import soot.jimple.infoflow.sourcesSinks.definitions.StatementSourceSinkDefinition;

import java.util.List;
import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;


public class CyfiSourceSinkHelper {
    public static void addAdditionalSourceNSinkDefToProvider(ISourceSinkDefinitionProvider sourceSinkDefinitionProvider, SootMethod entryPointMethod, InfoflowConfiguration config){
//        for(SootClass clz: Scene.v().getClasses()){
//            for(SootMethod sm: clz.getMethods()){
//                if(sm.getName().contains("loadClass")){
//                    System.out.println(sm.getSignature());
//                }
//            }
//
//        }

        Collection<ISourceSinkDefinition> sources = (Collection<ISourceSinkDefinition>) sourceSinkDefinitionProvider.getSources();
        if(config.getCyfiConfiguration().getUseJSIEntryPoint() && entryPointMethod != null){
            sources.clear();
            if(entryPointMethod.getDeclaringClass().resolvingLevel() == SootClass.SIGNATURES){
                Scene.v().addBasicClass(entryPointMethod.getDeclaringClass().getName(), SootClass.BODIES);
                entryPointMethod.getDeclaringClass().setResolvingLevel(SootClass.BODIES);
//                entryPointMethod.getDeclaringClass().setResolvingLevel(SootClass.BODIES);
            }
            if(entryPointMethod.isPhantom()){
                entryPointMethod.setPhantom(false);
                entryPointMethod.retrieveActiveBody();
            }

            for(Unit u: entryPointMethod.getActiveBody().getUnits()){
                if(u instanceof IdentityStmt) {
                    IdentityStmt stmt = (IdentityStmt) u;
                    Value rightValue = stmt.getRightOp();
                    Value leftValue = stmt.getLeftOp();
                    if(rightValue instanceof ParameterRef && leftValue instanceof Local){
                        ISourceSinkDefinition def = new StatementSourceSinkDefinition(stmt, (Local) leftValue, new HashSet<>());
                        sources.add(def);
                    }
                }
            }
        }
//        if(config.getCyfiConfiguration().isDynamicLoading()){
//            // we need to patch Volley network call api as a valid source
//            // set the statement of identifyStmt as a valid source
//            try {
//                // 1st, find all methods implements listener interface
//                List<SootClass> volleyListeners = Scene.v().getClasses().stream().filter(c -> c.implementsInterface("com.android.volley.Response$Listener") && !CyfiInfoflowHelper.v().isClassInAdditionalIgnore(c.getName())).collect(Collectors.toList());
//                for(SootClass volleyListener: volleyListeners){
//                    try {
//                        SootMethod onResponseMethod = volleyListener.getMethods().stream().filter(m -> m.getName().equals("onResponse")).findFirst().get();
//                        if(!onResponseMethod.hasActiveBody()) onResponseMethod.retrieveActiveBody();
//                        if(onResponseMethod.isConcrete() && onResponseMethod.hasActiveBody()){
//                            for(Unit u: onResponseMethod.getActiveBody().getUnits()){
//                                if(u instanceof IdentityStmt){
//                                    IdentityStmt stmt = (IdentityStmt) u;
//                                    Value rightValue = stmt.getRightOp();
//                                    Value leftValue = stmt.getLeftOp();
//                                    if(rightValue instanceof ParameterRef && leftValue instanceof Local){
//                                        ISourceSinkDefinition def = new StatementSourceSinkDefinition(stmt, (Local) leftValue, new HashSet<>());
//                                        sources.add(def);
//                                    }
//                                }
//                            }
//                        }
//                    } catch(Exception ignored) {
//                        //on response not exist, do nothing.
//                    }
//                }
//            }catch (Exception e) {
//                e.printStackTrace();
//            }
//        }

        return;
    }

}
