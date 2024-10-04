package soot.jimple.infoflow.cyfi.cfg;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.cfg.FlowDroidEssentialMethodTag;
import soot.jimple.infoflow.cyfi.infoflow.CyfiInfoflowHelper;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.util.SystemClassHandler;
import soot.jimple.toolkits.callgraph.CallGraph;

import java.util.*;
import java.util.stream.Collectors;

public class CyfiLibraryClassPatcher {
    public CyfiLibraryClassPatcher() {
    }

    /**
     * Patches all supported system libraries
     */
    public void patchExtraLibraries() {
        patchExtraHandlerImplementation();
        searchAndPatchExecuteAndSubmitImplementation();
    }

    public void searchAndPatchExecuteAndSubmitImplementation(){
        Collection<SootClass>  scs = Scene.v().getClasses();
        List<SootMethod> patchedMethods = new ArrayList<>();
        for(SootClass sc: scs){
            if(sc.getName().contains("AbstractExecutorService")){
                int i = 0;
                if((sc.getModifiers() & Modifier.ABSTRACT) != 0){
                    sc.setModifiers(sc.getModifiers() - Modifier.ABSTRACT);
                }
            } else if(sc.getName().equals("com.example.testcase.MainActivity")){
                SootMethod sm = sc.getMethodUnsafe("void decompile(java.util.Collection)");
                if(!sm.hasActiveBody()){
                    sm.retrieveActiveBody();

                }
            }
            if(!sc.isConcrete()) continue;;
           for(SootMethod sm: sc.getMethods()){
//              if(sm.isAbstract() || sm.isNative()) continue;
               if((sm.getName().startsWith("execute") || sm.getName().startsWith("submit")) // submit(Runnable) or execute(runnable) or submit(Callable)
                       && sm.getParameterCount() > 0
                       && (sm.getParameterType(0).toString().equals("java.lang.Runnable") || sm.getParameterType(0).toString().equals("java.util.concurrent.Callable"))){

                   // patch the body for execute method based on parameter type
                   this.patchExecuteMethodImplementation(sc, sm);

                   //
                   String keyMethod = sm.getParameterType(0).toString().equals("java.lang.Runnable") ? "<java.lang.Runnable: void run()>" : "<java.util.concurrent.Callable: java.lang.Object call()>";
                   String actualTargetMethod =  sm.getParameterType(0).toString().equals("java.lang.Runnable") ? "void run()" : "java.lang.Object call()";
                   CyfiInfoflowHelper.v().addMethodToBackTrackingMap(keyMethod, new CyfiInfoflowHelper.TargetMethodnArg(sc, sm.getName(), sm.getParameterType(0).toString(), actualTargetMethod));
                   patchedMethods.add(sm);
               }else if(sm.getName().equals("invokeAll") || sm.getName().equals("invokeAny")){  // Also patch invokeAll and invokeAny here as additional case
                   this.patchExecutorServiceInvokeAllOrInvokeAnyMethodImplementation(sc, sm);
                   patchedMethods.add(sm);
               }
           }
        }
        System.out.println("Patched methods: " + patchedMethods);
    }

    private void patchExecuteMethodImplementation(SootClass sc, SootMethod method){
        if(sc == null || sc.resolvingLevel() < SootClass.SIGNATURES) return;
        if(!sc.isLibraryClass()) sc.setLibraryClass();
        if(method== null || (method.hasActiveBody() && !SystemClassHandler.v().isStubImplementation(method.getActiveBody()))) {
            return;
        }
        Body b = addBasicBodyForVirtualInvokeMethod(method);
        for(int i = 0; i < method.getParameterCount(); i ++){
            try{
                Type parameterType = method.getParameterType(i);
                if(parameterType.toString().equals("java.lang.Runnable")){
                    SootClass runnableClz = Scene.v().getSootClassUnsafe("java.lang.Runnable");
                    SootMethod runMethod = runnableClz.getMethodUnsafe("void run()");
                    if(runMethod != null){
                        SootMethodRef runMethodRef = runMethod.makeRef();
                        Local runnableLocal = getFirstParamLocalByTypeMatching(b, runnableClz.getType());
                        b.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newInterfaceInvokeExpr(runnableLocal, runMethodRef)));
                        break;
                    }

                }
                else if(parameterType.toString().equals("java.util.concurrent.Callable")){
                    SootClass callableClz = Scene.v().getSootClassUnsafe("java.util.concurrent.Callable");
                    SootMethod callMethod = callableClz.getMethodUnsafe("java.lang.Object call()");
                    if(callMethod != null){
                        SootMethodRef callMethodRef = callMethod.makeRef();
                        Local callableLocal = getFirstParamLocalByTypeMatching(b, callableClz.getType());
                        b.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newInterfaceInvokeExpr(callableLocal, callMethodRef)));
                        break;
                    }
                }
            } catch(Exception ignored){
                ;
            }
        }
        patchReturnForMethod(method);
        b.validate();

        method.addTag(new FlowDroidEssentialMethodTag());
        return;
    }


    private void patchExecutorServiceInvokeAllOrInvokeAnyMethodImplementation(SootClass sc, SootMethod method){
        if(sc == null || sc.resolvingLevel() < SootClass.SIGNATURES) return;
        if(!sc.isLibraryClass()) sc.setLibraryClass();
        if(method== null || (method.hasActiveBody() && !SystemClassHandler.v().isStubImplementation(method.getActiveBody()))) {
            return;
        }
        Body b = addBasicBodyForVirtualInvokeMethod(method);

        Local r1 = Jimple.v().newLocal("r1", RefType.v("java.util.Iterator"));
        b.getLocals().add(r1);
        Local collectionParam = getFirstParamLocalByTypeMatching(b, "java.util.Collection");
        b.getUnits().add(Jimple.v().newAssignStmt(r1, Jimple.v().newVirtualInvokeExpr(collectionParam, Scene.v().getMethod("<java.util.Collection: java.util.Iterator iterator()>").makeRef())));

        Local r2 = Jimple.v().newLocal("r2", RefType.v("java.lang.Object"));
        b.getLocals().add(r2);
        b.getUnits().add(Jimple.v().newAssignStmt(r2, Jimple.v().newVirtualInvokeExpr(r1, Scene.v().getMethod("<java.util.Iterator: java.lang.Object next()>").makeRef())));

        Local r3 = Jimple.v().newLocal("r3", RefType.v("java.util.concurrent.Callable"));
        b.getLocals().add(r3);
        b.getUnits().add(Jimple.v().newAssignStmt(r3, Jimple.v().newCastExpr(r2, RefType.v("java.util.concurrent.Callable"))));

        b.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newInterfaceInvokeExpr(r3, Scene.v().getMethod("<java.util.concurrent.Callable: java.lang.Object call()>").makeRef())));

        patchReturnForMethod(method);
        b.validate();
        method.addTag(new FlowDroidEssentialMethodTag());
    }

    private void patchExtraHandlerImplementation(){
        SootClass sc = Scene.v().getSootClassUnsafe("android.os.Handler");
        if (sc == null || sc.resolvingLevel() < SootClass.SIGNATURES)
            return;
        if(!sc.isLibraryClass()) sc.setLibraryClass();

        Collection<SootMethod> sms = sc.getMethods();
        List<String> sigs = sms.stream().map(SootMethod::getSignature).collect(Collectors.toList());


        SootMethod handlerSendMessage = sc.getMethodUnsafe("boolean sendMessage(android.os.Message)");
        if(handlerSendMessage != null){
            this.patchHandlerSendMessageImplementation(handlerSendMessage);
            handlerSendMessage.addTag(new FlowDroidEssentialMethodTag());
        }

        SootMethod handlerSendMessageAtFrontOfQueue = sc.getMethodUnsafe("boolean sendMessageAtFrontOfQueue(android.os.Message)");
        if(handlerSendMessageAtFrontOfQueue != null){
            this.patchHandlerSendMessageImplementation(handlerSendMessageAtFrontOfQueue);
            handlerSendMessageAtFrontOfQueue.addTag(new FlowDroidEssentialMethodTag());
        }


        SootMethod handlerSendMessageAtTime = sc.getMethodUnsafe("boolean sendMessageAtTime(android.os.Message,long)");
        if(handlerSendMessageAtTime != null){
            this.patchHandlerSendMessageImplementation(handlerSendMessageAtTime);
            handlerSendMessageAtTime.addTag(new FlowDroidEssentialMethodTag());
        }


        SootMethod handlerSendMessageDelayed = sc.getMethodUnsafe("boolean sendMessageDelayed(android.os.Message,long)");
       if(handlerSendMessageDelayed != null){
           this.patchHandlerSendMessageImplementation(handlerSendMessageDelayed);
           handlerSendMessageDelayed.addTag(new FlowDroidEssentialMethodTag());
    }


       SootMethod handlerSendEmptyMessage = sc.getMethodUnsafe("boolean sendEmptyMessage(int)");
       if(handlerSendEmptyMessage != null){
           this.patchHandlerSendEmptyMessageImplementation(handlerSendEmptyMessage);
           handlerSendEmptyMessage.addTag(new FlowDroidEssentialMethodTag());
       }


        SootMethod handlerSendEmptyMessageDelayed = sc.getMethodUnsafe("boolean sendEmptyMessageDelayed(int,long)");
        if(handlerSendEmptyMessageDelayed != null){
            this.patchHandlerSendEmptyMessageImplementation(handlerSendEmptyMessageDelayed);
            handlerSendEmptyMessageDelayed.addTag(new FlowDroidEssentialMethodTag());
        }

        SootMethod handlerSendEmptyMessageAtTime = sc.getMethodUnsafe("boolean sendEmptyMessageAtTime(int,long)");
        if(handlerSendEmptyMessageAtTime != null){
            this.patchHandlerSendEmptyMessageImplementation(handlerSendEmptyMessageAtTime);
            handlerSendEmptyMessageAtTime.addTag(new FlowDroidEssentialMethodTag());
        }

        SootMethod handlerPostMessage = sc.getMethodUnsafe("boolean post(java.lang.Runnable)");
        if(handlerPostMessage != null){
            this.patchHandlerPostImplementation(handlerPostMessage);
            handlerPostMessage.addTag(new FlowDroidEssentialMethodTag());
        }


        SootMethod handlerPostAtFrontOfQueue = sc.getMethodUnsafe("boolean postAtFrontOfQueue(java.lang.Runnable)");
        if(handlerPostAtFrontOfQueue != null){
            this.patchHandlerPostImplementation(handlerPostAtFrontOfQueue);
            handlerPostAtFrontOfQueue.addTag(new FlowDroidEssentialMethodTag());
        }

        SootMethod handlerPostAtTimeWithToken = sc.getMethodUnsafe("boolean postAtTime(java.lang.Runnable,java.lang.Object,long)");
        if(handlerPostAtTimeWithToken != null){
            this.patchHandlerPostImplementation(handlerPostAtTimeWithToken);
            handlerPostAtTimeWithToken.addTag(new FlowDroidEssentialMethodTag());
        }

        SootMethod handlerPostAtTime = sc.getMethodUnsafe("boolean postAtTime(java.lang.Runnable,long)");
        if(handlerPostAtTime != null){
            this.patchHandlerPostImplementation(handlerPostAtTime);
            handlerPostAtTime.addTag(new FlowDroidEssentialMethodTag());
        }

        SootMethod handlerPostDelayed = sc.getMethodUnsafe("boolean postDelayed(java.lang.Runnable,long)");
        if(handlerPostDelayed != null){
            this.patchHandlerPostImplementation(handlerPostDelayed);
            handlerPostDelayed.addTag(new FlowDroidEssentialMethodTag());
        }

        SootMethod handlerPostDelayedWithObj = sc.getMethodUnsafe("boolean postDelayed(java.lang.Runnable,java.lang.Object,long)");
        if(handlerPostDelayedWithObj != null){
            this.patchHandlerPostImplementation(handlerPostDelayedWithObj);
            handlerPostDelayedWithObj.addTag(new FlowDroidEssentialMethodTag());
        }

        SootMethod handlerDispatchMessage = sc.getMethodUnsafe("void dispatchMessage(android.os.Message)");
        if(handlerDispatchMessage != null){
            this.patchHandlerDispatchImplementation(handlerDispatchMessage);
            handlerDispatchMessage.addTag(new FlowDroidEssentialMethodTag());
        }
    }


    private void patchHandlerDispatchImplementation(SootMethod method){
        if(method== null || (method.hasActiveBody() && !SystemClassHandler.v().isStubImplementation(method.getActiveBody()))){
            return;
        }

        Body b = addBasicBodyForVirtualInvokeMethod(method);
        Local thisLocal = this.getThisLocalFromBody(b);

        // 1> add a this.handleMessage(message) call

        SootMethod handleMessageMethod = method.getDeclaringClass().getMethodUnsafe("void handleMessage (android.os.Message)");
        Type messageType = Scene.v().getSootClassUnsafe("android.os.Message").getType();
        Local messageLocal = this.getFirstParamLocalByTypeMatching(b, messageType);
        if(thisLocal != null && handleMessageMethod != null){
            b.getUnits().add( Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(thisLocal, handleMessageMethod.makeRef(), messageLocal)));
        }

        // 2> add a r1 = message
        Local r1 = Jimple.v().newLocal("r1", messageType);
        AssignStmt assignStmt = Jimple.v().newAssignStmt(r1, messageLocal);
        b.getLocals().add(r1);
        b.getUnits().add(assignStmt);

        // 2> add a r1 = msessage.callback
        Local r2 = Jimple.v().newLocal("r2", RefType.v("java.lang.Runnable"));
        AssignStmt callbackAssignStmt = Jimple.v().newAssignStmt(r2, Jimple.v().newInstanceFieldRef(assignStmt.getLeftOp(), Scene.v().getField("<android.os.Message: java.lang.Runnable callback>").makeRef()));
        b.getLocals().add(r2);
        b.getUnits().add(callbackAssignStmt);
        // 3> add a r1.run() call
        SootMethod runMethod = Scene.v().getSootClassUnsafe("java.lang.Runnable").getMethodUnsafe("void run()");
        if(runMethod != null){
            b.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(r2, runMethod.makeRef())));
        }

        // 4> get handler.callback: r3 = this.callback
        SootClass callbackClass = Scene.v().getSootClassUnsafe("android.os.Handler$Callback");
        Local r3 = Jimple.v().newLocal("r3", callbackClass.getType());

        SootClass handlerClass = Scene.v().getSootClassUnsafe("android.os.Handler");

        SootField callbackField = handlerClass.getFieldUnsafe("android.os.Handler$Callback mCallback");
        if (callbackField == null) {
            callbackField = Scene.v().makeSootField("mCallback", RefType.v("android.os.Handler$Callback"));
            handlerClass.addField(callbackField);
        }


        AssignStmt callbackAssignStmt2 = Jimple.v().newAssignStmt(r3, Jimple.v().newInstanceFieldRef(thisLocal, callbackField.makeRef()));
        b.getLocals().add(r3);
        b.getUnits().add(callbackAssignStmt2);

        // 5> add a r3.handleMessage(message) call
        SootMethod handleMessageMethod2 = callbackClass.getMethodUnsafe("boolean handleMessage (android.os.Message)");
        if(handleMessageMethod2 != null){
            b.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(r3, handleMessageMethod2.makeRef(), messageLocal)));
        }

        this.patchReturnForMethod(method);
        b.validate();

    }
    private void patchHandlerPostImplementation(SootMethod method){
        if(method== null || (method.hasActiveBody() && !SystemClassHandler.v().isStubImplementation(method.getActiveBody()))){
            return;
        }

        Body b = addBasicBodyForVirtualInvokeMethod(method);
        Local thisLocal = this.getThisLocalFromBody(b);

        SootClass messageClz = Scene.v().getSootClassUnsafe("android.os.Message");
        Type messageType = messageClz.getType();

        // Add Message r1 = new Message();
        Local newMessageLocal = Jimple.v().newLocal("r1", messageType);
        NewExpr newMessageExpr = Jimple.v().newNewExpr((RefType) messageType);
        b.getLocals().add(newMessageLocal);
        b.getUnits().add(Jimple.v().newAssignStmt(newMessageLocal, newMessageExpr));

        // Add r1.init();
        b.getUnits().add(Jimple.v().newInvokeStmt( Jimple.v().newSpecialInvokeExpr(newMessageLocal, messageClz.getMethodUnsafe("void <init>()").makeRef())));

        // Add r1.callback = param0
        SootField callbackField = messageClz.getFieldUnsafe("java.lang.Runnable callback");
        if (callbackField == null) {
            Type runnableType = Scene.v().getSootClassUnsafe("java.lang.Runnable").getType();
            callbackField = Scene.v().makeSootField("callback", runnableType);
            messageClz.addField(callbackField);
        }

        Value messageCallbackField = Jimple.v().newInstanceFieldRef(newMessageLocal, callbackField.makeRef());
        Local runnableLocal = this.getFirstParamLocalByTypeMatching(b, "java.lang.Runnable");
        b.getUnits().add(Jimple.v().newAssignStmt(messageCallbackField, runnableLocal));

        SootMethod handleMessageMethod = method.getDeclaringClass().getMethodUnsafe("void dispatchMessage(android.os.Message)");
        if(thisLocal != null && handleMessageMethod != null){
            b.getUnits().add( Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(thisLocal, handleMessageMethod.makeRef(), newMessageLocal)));
        }

        patchReturnForMethod(method);
        b.validate();
    }


    private void patchHandlerSendMessageImplementation(SootMethod method){
        if(method== null || (method.hasActiveBody() && !SystemClassHandler.v().isStubImplementation(method.getActiveBody()))){
            return;
        }
        Body b = addBasicBodyForVirtualInvokeMethod(method);

        // Add actual body after param identity
        Local thisLocal = this.getThisLocalFromBody(b);
        Type messageType = Scene.v().getSootClassUnsafe("android.os.Message").getType();
        Local messageLocal = this.getFirstParamLocalByTypeMatching(b, messageType);

        SootMethod handleMessageMethod = method.getDeclaringClass().getMethodUnsafe("void dispatchMessage(android.os.Message)");
        if(thisLocal != null && handleMessageMethod != null){
            b.getUnits().add( Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(thisLocal, handleMessageMethod.makeRef(), messageLocal)));
        }

        patchReturnForMethod(method);
        // End of Body Constructing
        b.validate();
    }

    private void patchHandlerSendEmptyMessageImplementation(SootMethod method){
        if(method== null || (method.hasActiveBody() && !SystemClassHandler.v().isStubImplementation(method.getActiveBody()))){
            return;
        }
        Body b = addBasicBodyForVirtualInvokeMethod(method);

        // Add actual body after param identity
        Local thisLocal = this.getThisLocalFromBody(b);
        SootClass messageClz = Scene.v().getSootClassUnsafe("android.os.Message");
        Type messageType = messageClz.getType();

        // Add Message r1 = new Message();
        Local newMessageLocal = Jimple.v().newLocal("r1", messageType);
        NewExpr newMessageExpr = Jimple.v().newNewExpr((RefType) messageType);
        b.getLocals().add(newMessageLocal);
        b.getUnits().add(Jimple.v().newAssignStmt(newMessageLocal, newMessageExpr));

        // Add r1.init();
        b.getUnits().add(Jimple.v().newInvokeStmt( Jimple.v().newSpecialInvokeExpr(newMessageLocal, messageClz.getMethodUnsafe("void <init>()").makeRef())));

        // Add r1.what = param
        SootField whatField = messageClz.getFieldUnsafe("int what");
        if (whatField == null) {
            whatField = Scene.v().makeSootField("what", IntType.v());
            messageClz.addField(whatField);
        }
        Value messageWhatField = Jimple.v().newInstanceFieldRef(newMessageLocal, whatField.makeRef());
        Local whatLocal = this.getFirstParamLocalByTypeMatching(b, "int");
        b.getUnits().add(Jimple.v().newAssignStmt(messageWhatField, whatLocal));

        SootMethod handleMessageMethod = method.getDeclaringClass().getMethodUnsafe("void dispatchMessage(android.os.Message)");
        if(thisLocal != null && handleMessageMethod != null){
            b.getUnits().add( Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(thisLocal, handleMessageMethod.makeRef(), newMessageLocal)));
        }

        patchReturnForMethod(method);
        // End of Body Constructing
        b.validate();
    }



    // ##################
    // Additional Utility functions for method patching
    // ##################

    private Body addBasicBodyForVirtualInvokeMethod(SootMethod method){
        SootClass sc = method.getDeclaringClass();
        if(!sc.isLibraryClass()) sc.setLibraryClass();
        method.setPhantom(false);


        Body b = Jimple.v().newBody(method);
        method.setActiveBody(b);

        if(!method.isStatic()){
            Local thisLocal = Jimple.v().newLocal("this", sc.getType());
            b.getLocals().add(thisLocal);
            b.getUnits().add(Jimple.v().newIdentityStmt(thisLocal, Jimple.v().newThisRef(sc.getType())));
        }

        //Assign Parameters
        for(int i = 0; i < method.getParameterCount(); i ++){
            Local paramLocal = Jimple.v().newLocal("param" + i, method.getParameterType(i));
            b.getLocals().add(paramLocal);
            b.getUnits().add(Jimple.v().newIdentityStmt(paramLocal, Jimple.v().newParameterRef(method.getParameterType(i), i)));
        }
        return b;
    }
    private void patchReturnForMethod(SootMethod method){
        if(method.getActiveBody().getUnits().getLast() instanceof ReturnStmt){
            return;
        }

        Type returnType = method.getReturnType();
        if(returnType instanceof VoidType){
            method.getActiveBody().getUnits().add(Jimple.v().newReturnVoidStmt());
            return;
        }

        SootClass clz = Scene.v().getSootClassUnsafe(returnType.toString());
        if(clz.getName().equals("boolean") || clz.getName().equals("int")){
            method.getActiveBody().getUnits().add(Jimple.v().newReturnStmt(IntConstant.v(1)));
            return;
        }else if(clz.getName().equals("float")){
            method.getActiveBody().getUnits().add(Jimple.v().newReturnStmt(FloatConstant.v(1.0f)));
        }else if(clz.getName().equals("java.lang.String")){
            method.getActiveBody().getUnits().add(Jimple.v().newReturnStmt(StringConstant.v("FakeString!")));
        }else {
            method.getActiveBody().getUnits().add(Jimple.v().newReturnStmt(NullConstant.v()));
        }
        if(!(method.getActiveBody().getUnits().getLast() instanceof ReturnStmt)) {
            throw new RuntimeException("The return type patching fails due to unknown return type: " + returnType.toString());
        }
        // For developing purpose, we must raise an exception for unhandled return type;
    }


    private Local getThisLocalFromBody(Body b){
        for(Local l: b.getLocals()){
            if(l.getName().equals("this")) return l;
        }
        return null;
    }


    private Local getFirstParamLocalByTypeMatching(Body b, String typeString){
        for(Local l: b.getLocals()){
            if(l.getName().startsWith("param") && l.getType().toString().equals(typeString)){
                return l;
            }
        }
        return null;
    }

    private Local getFirstParamLocalByTypeMatching(Body b, Type type){
        for(Local l: b.getLocals()){
            if(l.getName().startsWith("param") && l.getType().equals(type)){
                return l;
            }
        }
        return null;
    }

    private Set<String> getAllCalledMethodFromEntryPoint(SootMethod entryPointMethod, IInfoflowCFG icfg, Set<String> systemApiCalled){
        if(systemApiCalled == null) systemApiCalled = new HashSet<>();

        for(Unit unit: icfg.getCallsFromWithin(entryPointMethod)){
            if(!(unit instanceof Stmt)) continue;
            Stmt stmt = (Stmt) unit;
            if(!stmt.containsInvokeExpr()) continue;
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            SootMethod calledMethod = invokeExpr.getMethod();


            if(calledMethod.getDeclaringClass().isApplicationClass()){
                systemApiCalled.add(calledMethod.getSignature());
            }
        }

        return systemApiCalled;
    }





}
