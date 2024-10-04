package com.cyfi.XposedPlugin.Modules;

import android.app.AndroidAppHelper;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.cyfi.XposedPlugin.XposedUtils;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ThreadCallStackHandler {
    static ThreadCallStackHandler instance = new ThreadCallStackHandler();
    public static ThreadCallStackHandler getInstance(){
        if(instance == null){
            instance = new ThreadCallStackHandler();
        }
        return instance;
    }

    public Map<Long, List<String>> threadToStackMap;
    public Map<Executor, Queue<ExecutorJobStack>> executorToJobStackMap;
    public Map<Long, Executor> threadToExecutorMap;
    public Map<Long, Thread> threadIdToThreadMap;
    public Map<String, Class> threadClassMap;
    public Map<Integer, List<String>> messageToStackMap;


    public ThreadCallStackHandler(){
        this.threadToStackMap = new Hashtable<>();
        this.executorToJobStackMap = new Hashtable<>();
        this.threadToExecutorMap = new Hashtable<>();
        this.threadIdToThreadMap = new Hashtable<>();
        this.threadClassMap = new Hashtable<>();
        this.messageToStackMap = new Hashtable<>();
    }


    public void hook(){
        hookThreadInit();
        hookThreadStart();
        hookExecutorInit();
        hookExecutorExecuteOrSubmitOrInvoke();
        hookExecutorAddWorker();
        hookHandlerEnqueueMessage();
        hookHandlerDispatchMessage();
//
//
    }

    public void hookHandlerDispatchMessage(){
        // hook dispatchMessage of Handler class
        XposedHelpers.findAndHookMethod("android.os.Handler", null, "dispatchMessage", android.os.Message.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                if(param.args.length > 0 && param.args[0] != null && param.args[0] instanceof Message){
                    Message message = (Message) param.args[0];
//                    XposedBridge.log("dispatchMessage: " + message.toString()  + " " + param.args[0].hashCode());
                    Integer messageHash = getMessageHashCode(message);
                    if(messageToStackMap.containsKey(messageHash)){
                        List<String> stack = messageToStackMap.getOrDefault(messageHash, new ArrayList<>());
                        Thread thread = Thread.currentThread();
                        threadToStackMap.put(thread.getId(), stack);
                    }else{
//                        XposedBridge.log("messageToStackMap does not contain message");
                        ;
                    }
                }

            }
        });

    }


    public void hookHandlerEnqueueMessage(){
        // hook enqueueMessage of Handler class
        XposedHelpers.findAndHookMethod("android.os.Handler", null, "enqueueMessage", android.os.MessageQueue.class, android.os.Message.class, long.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                if(param.args.length < 2 || !(param.args[1] instanceof Message)){
                    return;
                }

//                XposedBridge.log("enqueueMessage, " + param.args[1].toString() + " " + param.args[1].hashCode());

                Message message = (Message) param.args[1];
                List<String> stack = getCallStack(param);
                messageToStackMap.put(getMessageHashCode(message), stack);

//                for(String stackItem: stack){
//                    XposedBridge.log("enqueueMessage Stack: " + stackItem);
//                }


            }
        });

    }

    public  void hookThreadInit(){
        XposedBridge.hookAllConstructors(Thread.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Thread thread = (Thread) param.thisObject;
//                XposedBridge.log("Thread init " + thread.getName());
                threadClassMap.put(thread.getClass().getName(), thread.getClass());
                List<String> stackForInit = getCallStack(param);
                threadIdToThreadMap.put(thread.getId(), thread);
                threadToStackMap.put(thread.getId(), stackForInit);

            }
        });
    }

    public void hookThreadStart(){
        XposedBridge.hookAllMethods(Thread.class, "start", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                Thread thread = (Thread) param.thisObject;
                // we should update the call stack for the thread
                List<String> externalStack = getCallStack(param);
//                XposedBridge.log("Thread start " + thread.getName());
//                for(StackTraceElement ste: Thread.currentThread().getStackTrace()){
//                    XposedBridge.log("*****" + ste.toString());
//                }

                threadToStackMap.put(thread.getId(), externalStack);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
            }
        });
    }

    public void hookExecutorInit(){
        XC_MethodHook executorConstructorHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Executor executor = (Executor) param.thisObject;
//                XposedBridge.log("Executor init " + executor.toString());
                if(!executorToJobStackMap.containsKey(executor)){
                    executorToJobStackMap.put(executor, new LinkedList<>());
                }
            }
        };

        XposedBridge.hookAllConstructors(ThreadPoolExecutor.class, executorConstructorHook);
        XposedBridge.hookAllConstructors(ForkJoinPool.class, executorConstructorHook);
    }

    public void hookExecutorExecuteOrSubmitOrInvoke(){
        XC_MethodHook executorExecuteHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);

                if(param.args.length == 0 || param.args[0] == null){
                    return;
                }

                Executor executor = (Executor) param.thisObject;

                List<String> externalStack = getCallStack(param);
                // get number of jub to be submitted
                int numJobs = 1;
                // the number will always be one since only one runnable or callable is passed in
                ExecutorJobStack jobStack = new ExecutorJobStack(numJobs, externalStack);
                if(executorToJobStackMap.containsKey(executor) && executorToJobStackMap.get(executor) != null){
                    executorToJobStackMap.get(executor).add(jobStack);
                }
//                XposedBridge.log("Executor execute " + executor.toString());
//
//                for(StackTraceElement ste: Thread.currentThread().getStackTrace()){
//                    XposedBridge.log("*****" + ste.toString());
//                }


                Runnable runnable = (Runnable) param.args[0];
                Class runnableClz = runnable.getClass();
                try{
                    XposedHelpers.findAndHookMethod(runnableClz, "run", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            Thread thread = Thread.currentThread();
                            long threadId = thread.getId();
                            if(threadToExecutorMap.containsKey(threadId)){
                                Executor executor = threadToExecutorMap.get(threadId);
                                if(executorToJobStackMap.containsKey(executor)){
                                    Queue<ExecutorJobStack> jobStackQueue = executorToJobStackMap.get(executor);
                                    if(jobStackQueue != null &&  jobStackQueue.size() > 0){
                                        ExecutorJobStack jobStack = jobStackQueue.peek();
                                        threadToStackMap.put(thread.getId(), jobStack.stack);
                                        if(jobStack.count > 0){
                                            jobStack.count--;
                                        }else{
                                            jobStackQueue.poll();
                                        }
                                    }
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                        }
                    });
                }catch (Exception e){
                    e.printStackTrace();
                }

            }
        };

        XposedBridge.hookAllMethods(ThreadPoolExecutor.class, "execute", executorExecuteHook);
        XposedBridge.hookAllMethods(ForkJoinPool.class, "execute", executorExecuteHook);
//        XposedBridge.hookAllMethods(ThreadPoolExecutor.class, "submit", executorExecuteHook);
//        XposedBridge.hookAllMethods(ForkJoinPool.class, "submit", executorExecuteHook);

        XC_MethodHook executorInvokeAllHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                Executor executor = (Executor) param.thisObject;
                List<String> externalStack = getCallStack(param);
                // get number of jub to be submitted
                int numJobs = 0;
                if(param.args.length > 0 && param.args[0] instanceof Collection){
                    numJobs = ((Collection)param.args[0]).size();
                }
//                XposedBridge.log("Executor invokeAll " + executor.toString() + " " + numJobs);
                // the number will always be one since only one runnable or callable is passed in
                ExecutorJobStack jobStack = new ExecutorJobStack(numJobs, externalStack);
                if(executorToJobStackMap.containsKey(executor) && executorToJobStackMap.get(executor) != null){
                    executorToJobStackMap.get(executor).add(jobStack);
                }
            }
        };
//
//        XposedBridge.hookAllMethods(ThreadPoolExecutor.class, "invokeAll", executorInvokeAllHook);
//        XposedBridge.hookAllMethods(ThreadPoolExecutor.class, "invokeAny", executorInvokeAllHook);
//
//        XposedBridge.hookAllMethods(ForkJoinPool.class, "invokeAll", executorInvokeAllHook);
//        XposedBridge.hookAllMethods(ForkJoinPool.class, "invokeAny", executorInvokeAllHook);
    }


    public void hookExecutorAddWorker(){
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Executor executor = (Executor) param.thisObject;
                Set<Thread> threads = getThreadsFromExeuctor(executor);
//                XposedBridge.log("Executor add worker " + executor.toString() + " " + threads.size());
                for(Thread thread: threads){
                    if(!threadToExecutorMap.containsKey(thread)){
                        threadToExecutorMap.put(thread.getId(), executor);
                    }
                }
            }
        };

        XposedBridge.hookAllMethods(ThreadPoolExecutor.class, "addWorker", hook);
        XposedBridge.hookAllMethods(ForkJoinPool.class, "addWorker", hook);

    }

    public void printThreadStack(){
        for(long threadId: threadToStackMap.keySet()){
            try{
//                XposedBridge.log("Thread " + threadIdToThreadMap.getOrDefault(threadId, null).getName());
//                for(String stack: threadToStackMap.get(threadId)){
//                    XposedBridge.log("===" + stack);
//                }
            }catch (NullPointerException e){
                XposedBridge.log("Thread " + threadId + " not found");
            }
        }
    }

    public List<String> getCallStack(){
        return getCallStack(null);
    }

    public List<String> getCallStack(XC_MethodHook.MethodHookParam param){
        StackTraceElement[] stackTraceElements =  Thread.currentThread().getStackTrace();
        List<String> res =new ArrayList<>();
        if(param != null){
            try{
                res.add(param.method.getDeclaringClass().getName() + ":" + param.method.getName() + ":" + 0);
            }catch(Exception ignored){
            }
        }
        boolean findHook = false;
        boolean isWithinHandler = false;
        StackTraceElement last = null;
        for(StackTraceElement ste: stackTraceElements){
            if(ste.getClassName().equals("de.robv.android.xposed.MethodHooker") && ste.getMethodName().equals("handleHookedMethod")){
                continue;
            }
            if(ste.getClassName().equals("java.lang.reflect.Method") && ste.getMethodName().equals("invoke") && ste.getLineNumber() == -2){
                continue;
            }

            if(ste.getClassName().startsWith("EdHooker") && ste.getMethodName().equals("hook")){
                if(!findHook) findHook = true;
                continue;
            }

            if(ste.getMethodName().equals("loop")){
                Class looperClz = null;
                try {
                    looperClz = Class.forName(ste.getClassName());
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }

                if(looperClz != null && Looper.class.isAssignableFrom(looperClz)){
                    isWithinHandler = true;
                    break;
                }
            }

            if(findHook){
                res.add(ste.getClassName() + ":" + ste.getMethodName() + ":" + ste.getLineNumber());
                last = ste;
            }
        }

        res.add("End of Thread Stack at " + Thread.currentThread().getName() + " " + Thread.currentThread().getId());
        Thread currentThread = Thread.currentThread();

        if(isWithinHandler && threadToStackMap.containsKey(currentThread.getId())){
            List<String> stack = threadToStackMap.get(currentThread.getId());
            if(stack != null && stack.size() > 0){
                res.addAll(stack);
            }
            return res;
        }

        // terminate the following stack trace if there is noway a thread run happened
        if(last == null) {
            return res;
        }

        Class<?> lastClass = threadClassMap.getOrDefault(last.getClassName(), null);
        try{
            try{
                lastClass = Class.forName(last.getClassName());
            }catch (Exception e) {
                e.printStackTrace();
            }

            if(lastClass == null){
                lastClass = XposedHelpers.findClass(last.getClassName(), AndroidAppHelper.currentApplication().getClassLoader());
            }

            XposedBridge.log("Last class " + lastClass.getName() + " is Thread: " + Thread.class.isAssignableFrom(lastClass));
        }catch(Exception e){
            XposedBridge.log("Exception when get class " + last.getClassName());
        }



        if(lastClass != null && Thread.class.isAssignableFrom(lastClass) && threadToStackMap.containsKey(currentThread.getId())){
            // fetch external stack trace based on thread id
            List<String> externalStack = this.threadToStackMap.getOrDefault(currentThread.getId(), new ArrayList<String>());
            if(externalStack != null){
                XposedBridge.log("External stack found " + currentThread.getName());
                res.addAll(externalStack);
            }
        }
        return res;
    }

    private static class ExecutorJobStack{
        public int count;
        public List<String> stack;

        public ExecutorJobStack(int count, List<String> stack){
            this.count = count;
            this.stack = stack;
        }

    }

    private static Set<Thread> getThreadsFromExeuctor(Executor executor){
        Set<Thread> res = new HashSet<>();
        if(executor instanceof ThreadPoolExecutor || executor instanceof ForkJoinPool) {
            // get worker with reflection
            try {
                Field workers = XposedUtils.getFieldFromClassOrParent(executor.getClass(), "workers");
                if (workers == null) return res;

                workers.setAccessible(true);
                Object workersObj = workers.get(executor);
                if (workersObj instanceof Set) {
                    Set workersSet = (Set) workersObj;

                    Field threadField = null;
                    for (Object worker : workersSet) {
                        // get thread field from worker
                        if (threadField == null) {
                            threadField = XposedUtils.getFieldFromClassOrParent(worker.getClass(), "thread");
                            threadField.setAccessible(true);
                        }
                        try {
                            Object threadObj = threadField.get(worker);
                            if (threadObj instanceof Thread) res.add((Thread) threadObj);
                        } catch (Exception ignored) {
                            ;
                        }
                    }
                }
            } catch (Exception e) {
                XposedBridge.log("Exception when get threads from executor " + executor);
            }
        }
        return res;
    }


    public int getMessageHashCode(Message msg){
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(msg.what).append(msg.arg1).append(msg.arg2).append(msg.obj).append(msg.replyTo).append(msg.getCallback()).append(msg.getData());
        return builder.toHashCode();
    }
}
