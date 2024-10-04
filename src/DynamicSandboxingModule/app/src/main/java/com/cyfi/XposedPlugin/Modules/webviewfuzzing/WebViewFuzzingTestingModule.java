package com.cyfi.XposedPlugin.Modules.webviewfuzzing;

import android.app.AndroidAppHelper;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.WebView;

import com.cyfi.XposedPlugin.Modules.Module;
import com.cyfi.XposedPlugin.dynamic.DynamicAnalysisManager;
import com.cyfi.XposedPlugin.dynamic.flowdroid.FlowDroidResult;
import com.cyfi.XposedPlugin.dynamic.flowdroid.ResolvedMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class WebViewFuzzingTestingModule extends Module {


    private Map<String, Map<String, Object>> javascriptInterfaces;
    private boolean isWebViewFuzzingTestingEnabled;
    private boolean webViewMethodAsSink;

    private Map<String, String[]> prefixMap;
    private FuzzArg[] currentFuzzArgs;

    private String currentFuzzingUrl;
    private String currentFuzzingInterfaceName;

    public FlowDroidResult.SourceMethod currentSourceMethod;
    private Set<String> methodToSkip;
    public List<ResolvedMethod> jsiSinkMethods;

    public WebViewFuzzingTestingModule() {
        this.isWebViewFuzzingTestingEnabled = false;
        this.webViewMethodAsSink = false;
//        this.prefixMap =new HashMap<>();
        this.methodToSkip = new HashSet<>();
        this.javascriptInterfaces = new HashMap<>();

    }


    @Override
    public void loadModule(XC_LoadPackage.LoadPackageParam lpparam) {
        try{

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onAttachBaseContext(Context context) {

    }

    @Override
    public void onApplicationCreate() {
        try {
            hookWebViewAddJavascriptInterface();
            hookWebViewRemoveJavascriptInterface();
            hookWebViewLoadUrl();

            getSinksFromProvider();
        }catch (Exception e){
            XposedBridge.log(e.getLocalizedMessage());
            e.printStackTrace();
        }
    }


    public void notifySinkHit(XC_MethodHook.MethodHookParam param) {
        if(DynamicAnalysisManager.getInstance().getInWebViewJSInterfaceTesting()){
            XposedBridge.log("Sink hit: " +param.method.getClass().getName() + ":"+ param.method.getName());
            //TODO: compare the arguments with input parameters, and get results

            if(currentSourceMethod == null) return;

            Object[] args = param.args;
            XposedBridge.log(Arrays.toString(args));
            if(!(param.method instanceof Method)) return;
            ResolvedMethod resolvedSinkMethod = new ResolvedMethod((Method)param.method);
            ContentValues values = new ContentValues();

            values.put("sinkMethod", resolvedSinkMethod.getMethodSignature());
            values.put("entryPointMethod", currentSourceMethod.sourceMethodSignature);
            values.put("packageName", AndroidAppHelper.currentPackageName());
            values.put("entryPointArgs", Arrays.toString(currentFuzzArgs));
            values.put("sinkArgs", Arrays.toString(args));
            values.put("interfaceName", currentFuzzingInterfaceName);
            values.put("url", currentFuzzingUrl);

            AndroidAppHelper.currentApplication().getContentResolver().insert(Uri.parse("content://com.cyfi.autolauncher2.provider/jsiresult"), values);

        }
    }

    private void hookWebViewAddJavascriptInterface(){
        try{
            XposedBridge.hookAllMethods(WebView.class, "addJavascriptInterface", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if(param.args.length == 2){

                        Object webviewObj = param.thisObject;
                        if(!(webviewObj instanceof WebView)){
                            return;
                        }
                        WebView webView = (WebView) webviewObj;
                        String webViewName = webView.getClass().getName() + "@" + Integer.toHexString(webView.hashCode());

                        if(!javascriptInterfaces.containsKey(webViewName)){
                            javascriptInterfaces.put(webViewName, new HashMap<String, Object>());
                        }
                        Object obj = param.args[0];
                        String interfaceName = (String) param.args[1];
                        javascriptInterfaces.get(webViewName).put(interfaceName, obj);
                    }
                }
            });
        } catch (Throwable e){
            e.printStackTrace();
        }
    }

    private void hookWebViewRemoveJavascriptInterface(){
        try{
            XposedBridge.hookAllMethods(WebView.class, "removeJavascriptInterface", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if(param.args.length == 1){
                        Object webviewObj = param.thisObject;
                        if(!(webviewObj instanceof WebView)){
                            return;
                        }
                        WebView webView = (WebView) webviewObj;
                        String webViewName = webView.getClass().getName() + "@" + Integer.toHexString(webView.hashCode());
                        if(javascriptInterfaces.containsKey(webViewName)){
                            String interfaceName = (String) param.args[0];
                            if(javascriptInterfaces.get(webViewName).containsKey(interfaceName)){
                                javascriptInterfaces.get(webViewName).remove(interfaceName);
                            }
                        }
                    }
                }
            });
        } catch (Throwable e){
            e.printStackTrace();
        }
    }



    public void hookWebViewLoadUrl() throws ClassNotFoundException {

        Class webviewClz = WebView.class;

        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                Object webviewObj = param.thisObject;
                if(!(webviewObj instanceof WebView)){
                    return;
                }
                XposedBridge.log("WebViewFuzzingTestingModule: beforeHookedMethod");
                XposedBridge.log( String.valueOf(isCalledByFuzzingTesting()));

               if(isCalledByFuzzingTesting() || DynamicAnalysisManager.getInstance().getInWebViewJSInterfaceTesting()){
                    notifySinkHit(param);
                    param.setResult(null);
                    return;
                }

                String thisMethodName = param.method.getName();

                if(thisMethodName.equals("loadUrl")){
                    String url = (String) param.args[0];
                    currentFuzzingUrl = url;
                } else {
                    currentFuzzingUrl = "file";
                }

//                if(!(url.startsWith("http://") || url.startsWith("https://"))){
//                    // A local file
//                    XposedBridge.log("Local file: " + url);
//                    return;
//                }

                WebView webview = (WebView) webviewObj;
                String webViewName = webview.getClass().getName() + "@" + Integer.toHexString(webview.hashCode());

                DynamicAnalysisManager.getInstance().setInWebViewJSInterfaceTesting(true);
                FlowDroidResult flowDroidResult = DynamicAnalysisManager.getInstance().getFlowDroidResult();
                if(flowDroidResult == null){
                    XposedBridge.log("FlowDroidResult is null");
                    return;
                }
                XposedBridge.log("WebViewFuzzingTestingModule: Start fuzzing :");


                if(WebViewFuzzingTestingModule.this.javascriptInterfaces.containsKey(webViewName)) {
                    Map<String, Object> interfaces = WebViewFuzzingTestingModule.this.javascriptInterfaces.get(webViewName);

                    for(String key : interfaces.keySet()){
                        Object obj = interfaces.get(key);
                        if(obj == null) continue;

                        currentFuzzingInterfaceName = key;
                        XposedBridge.log("get javascript interface: " + obj.toString());
                        Method[] methods = obj.getClass().getDeclaredMethods();


                        if(methods == null) continue;
                        for(Method method: methods){
                            ResolvedMethod resolvedMethod = new ResolvedMethod(method);
                            if(WebViewFuzzingTestingModule.this.methodToSkip.contains(resolvedMethod.getMethodSignature())){
                                continue;
                            }
                            FlowDroidResult.SourceMethod sourceMethod = getFlowDroidResultMatch(resolvedMethod, flowDroidResult);
                            if(sourceMethod == null) continue;

                            XposedBridge.log("Found a matched interface method: " + resolvedMethod.getMethodSignature());
                            currentSourceMethod = new FlowDroidResult.SourceMethod(resolvedMethod.getMethodSignature());

                            List<FuzzArg[]> argBatch = getArgList(method, sourceMethod);
                            for(FuzzArg[] args: argBatch){
                                try{
                                    XposedBridge.log("Fuzzing Invoke: " + method.getName());
                                    XposedBridge.log(Arrays.toString(args));
                                    WebViewFuzzingTestingModule.this.currentFuzzArgs = args;
                                    fuzzingInvokeWrapper(method, obj, getMethodArgFromFuzzArgs(args));
//                                    method.invoke(obj, getMethodArgFromFuzzArgs(args));
                                } catch (Throwable e){
                                    XposedBridge.log("Fuzzing Method Exception: " + e.getMessage());
                                }
                                try{
                                    Thread.sleep(1000);
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }
                            WebViewFuzzingTestingModule.this.methodToSkip.add(resolvedMethod.getMethodSignature());
                        }
                    }
                }
                try{
                    Thread.sleep(1000);
                }catch (Exception e){
                    e.printStackTrace();
                }
                XposedBridge.log("Simulate WebVIew fuzzing finished");
                DynamicAnalysisManager.getInstance().setInWebViewJSInterfaceTesting(false);
            }
        };


        XposedBridge.hookAllMethods(webviewClz, "loadUrl", hook);
        XposedBridge.hookAllMethods(webviewClz, "loadData", hook);
        XposedBridge.hookAllMethods(webviewClz, "loadDataWithBaseURL", hook);
    }


    public void fuzzingInvokeWrapper(Method method, Object obj, Object... args){
        if(method == null) return;
        try{
            method.invoke(obj, args);
        }catch (Exception e){
            XposedBridge.log(e.toString());
        }
    }

    public FlowDroidResult.SourceMethod getFlowDroidResultMatch(ResolvedMethod resolvedMethod, FlowDroidResult flowDroidResult){
        return flowDroidResult.entryPoints.get(resolvedMethod.getMethodSignature());
    }

    public List<FuzzArg[]> getArgList(Method method, FlowDroidResult.SourceMethod sourceMethod){
        List<FuzzArg[]> results = new ArrayList<>();
        results.add(getArgs(method));
        return results;
    }

    public FuzzArg[] getArgs(Method method){
        FuzzArg[] objs = new FuzzArg[method.getParameterTypes().length];
        for(int i = 0; i < method.getParameterTypes().length; i++){
            objs[i] = new FuzzArg(method.getParameterTypes()[i]);
        }
        return objs;
    }

    public Object[] getMethodArgFromFuzzArgs(FuzzArg[] args){
        Object[] objs = new Object[args.length];
        for(int i = 0; i < args.length; i++){
            objs[i] = args[i].value;
        }
        return objs;
    }


    public void getSinksFromProvider(){
        if(this.jsiSinkMethods == null){
            this.jsiSinkMethods = new ArrayList<>();
        }
        this.jsiSinkMethods.clear();


        Cursor cursor = AndroidAppHelper.currentApplication().getContentResolver().query(Uri.parse("content://com.cyfi.autolauncher2.provider/jsisink"), new String[]{"id", "class", "method", "type", "line"}, null, null, null);
        if(cursor == null) return;
        while(cursor.moveToNext()){
            XposedBridge.log("Receive Sink/Source: " + cursor.getInt(0) + " " + cursor.getString(1) + " " + cursor.getString(2) + " " + cursor.getString(3));
            ResolvedMethod rm = new ResolvedMethod(cursor.getString((4)));
            this.jsiSinkMethods.add(rm);
        }
        cursor.close();

        XposedBridge.log("JSI Webview Fuzzing Module: " + this.jsiSinkMethods.size() + " sinks/sources received");

        hookSinkMethods();
    }




    public void hookSinkMethods() {
        if(jsiSinkMethods == null) return;
        for(ResolvedMethod resolvedMethod: jsiSinkMethods){
            try{
                Class<?> declaringClass =  AndroidAppHelper.currentApplication().getClassLoader().loadClass(resolvedMethod.declaringClass);
                if(declaringClass.getName().equals( "android.webkit.WebView")) {
                    this.webViewMethodAsSink = true;
                }
                XposedBridge.log("WebViewFuzzingTestingModule: Hooking sink method: " + resolvedMethod.getMethodSignature());
                XposedBridge.hookAllMethods(declaringClass, resolvedMethod.methodName, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        if(DynamicAnalysisManager.getInstance().getInWebViewJSInterfaceTesting()){
                            WebViewFuzzingTestingModule.this.notifySinkHit(param);
                            param.setResult(null);
                        }
                    }
                });
            }catch(Exception e){
                XposedBridge.log(e.getLocalizedMessage());
                e.printStackTrace();
            }
        }
    }

    public boolean isCalledByFuzzingTesting(){
        List<String> callStack = getCallStack();
        for(String stmt: callStack){
            if(stmt.contains("fuzzingInvokeWrapper")){
                return true;
            }
        }
        return false;
    }

    public List<String> getCallStack(){
        StackTraceElement[] stackTraceElements =  Thread.currentThread().getStackTrace();
        List<String> res =new ArrayList<>();
        boolean seeHook = false;
        for(StackTraceElement ste : stackTraceElements){
//            XposedBridge.log(ste.getClassName() + ":" + ste.getMethodName());
            if(ste.getClassName().startsWith("EdHooker_") && ste.getMethodName().equals("hook")) {
                seeHook = true;
            }
            if(seeHook) res.add(ste.getClassName() + ":" + ste.getMethodName() + ":" + ste.getLineNumber());
        }
        return res;
    }
}
