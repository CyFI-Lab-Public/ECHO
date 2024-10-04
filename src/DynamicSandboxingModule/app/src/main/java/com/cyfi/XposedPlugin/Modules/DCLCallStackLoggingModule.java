package com.cyfi.XposedPlugin.Modules;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.webkit.WebView;


import com.cyfi.XposedPlugin.XposedUtils;
import com.cyfi.XposedPlugin.dynamic.flowdroid.ResolvedMethod;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Filter;
import java.util.zip.ZipInputStream;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DCLCallStackLoggingModule extends Module {
    private static DCLCallStackLoggingModule instance = null;

    public static DCLCallStackLoggingModule getInstance() {
        if(instance == null) {
            instance = new DCLCallStackLoggingModule();
        }
        return instance;
    }

    public List<ResolvedMethod> dclSinkSourceMethods;
    public Context context;
    public String packageName;
    private Map<String, Map<String, Object>> javascriptInterfaces;


    public void loadModule(XC_LoadPackage.LoadPackageParam lpparam) {
        this.packageName = lpparam.packageName;
        this.javascriptInterfaces= new HashMap<>();
    }

    @Override
    public void onAttachBaseContext(Context context) {
        XposedBridge.log("Start of onAttachBaseContext for DCLCallStackLoggingModule");
        try{
            if(this.context == null) {
                this.context = context;
            }
            getSinksFromProvider(context);
            hookReflectionInvokeMethod();
            hookDclSourceSinkMethod();
            ThreadCallStackHandler.getInstance().hook();

        }catch (Exception e){
            XposedBridge.log(e.getLocalizedMessage());
            e.printStackTrace();
        }
        XposedBridge.log("End of onAttachBaseContext for DCLCallStackLoggingModule");

    }

    @Override
    public void onApplicationCreate() {
        Application application = AndroidAppHelper.currentApplication();
        XposedBridge.log("Start of onApplicationCreate for DCLCallStackLoggingModule " + application.getPackageName());
        if(application == null) return;
        this.context = application.getApplicationContext();
        if(this.context == null){
            XposedBridge.log("Cannot get application context on create");
        }

        hookWebViewAddJavascriptInterface();
        hookWebViewRemoveJavascriptInterface();
    }


    public void getSinksFromProvider(Context context){
        if(this.dclSinkSourceMethods == null){
            this.dclSinkSourceMethods = new ArrayList<>();
        }
        this.dclSinkSourceMethods.clear();


        Cursor cursor = context.getContentResolver().query(Uri.parse("content://com.cyfi.autolauncher2.provider/dclsinksource"), new String[]{"id", "class", "method", "type", "line"}, null, null, null);
        if(cursor == null) return;
        while(cursor.moveToNext()){
            XposedBridge.log("Receive Sink/Source: " + cursor.getInt(0) + " " + cursor.getString(1) + " " + cursor.getString(2) + " " + cursor.getString(3));
            ResolvedMethod rm = new ResolvedMethod(cursor.getString((4)));
            this.dclSinkSourceMethods.add(rm);
        }
        cursor.close();

        XposedBridge.log("DCLCallStackLoggingModule: " + this.dclSinkSourceMethods.size() + " sinks/sources received");

    }


    public void hookDclSourceSinkMethod(){
        XposedBridge.log("DCLCallStackLoggingModule: Start hook DCL source / sink, and reflection method");
        if(this.dclSinkSourceMethods == null) return;

        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                ResolvedMethod resolvedSinkMethod = null;
                try{
                    if(param.method instanceof Method){
                        resolvedSinkMethod = new ResolvedMethod((Method) param.method);
                    }else if(param.method instanceof Constructor){
                        resolvedSinkMethod = new ResolvedMethod((Constructor) param.method);
                    } else {
                        return;
                    }
                }catch (Exception e){
                    XposedBridge.log("DCLCallStackLoggingModule: " + e.getLocalizedMessage());
                }
                if(resolvedSinkMethod == null) return;

                XposedBridge.log("DCLCallStackLoggingModule: Hooking " + resolvedSinkMethod.declaringClass + " " + resolvedSinkMethod.methodName);
                for(Object arg: param.args){
                    XposedBridge.log("DCLCallStackLoggingModule: arg:  " + arg);
                }
                boolean isHookCall = XposedUtils.isHookCall();
                if(isHookCall){
//                    XposedBridge.log("DCLCallStackLoggingModule: Hook call, caller:" + XposedUtils.getCurrentCaller());
                    return;
                }else {
                    ;
//                    XposedBridge.log("DCLCallStackLoggingModule: Not hook call, caller:" + XposedUtils.getCurrentCaller());
                }
                List<String> stack = ThreadCallStackHandler.getInstance().getCallStack(param);
                ContentValues cv = new ContentValues();
                cv.put("method", resolvedSinkMethod.getMethodSignature());
                cv.put("packageName", AndroidAppHelper.currentPackageName());
//                // TODO: type, URL, filePath, loadClassName

                if(isNetworkMethod(param, resolvedSinkMethod)){
                    XposedBridge.log("DCLCallStackLoggingModule: Network method" + " " + resolvedSinkMethod.getMethodSignature());
                    cv.put("type", "network");
                    String url = getUrlFromNetworkConn(param);
                    if(url != null){
                        cv.put("url", url);
                    }
                }
                else if(isFileReadMethod(param, resolvedSinkMethod)){
                    cv.put("type", "fileRead");
                    String filePath = getFilePathFromFileReadAPIs(param);

                    if(filePath == null ||  filePath.contains("EdHooker_") || filePath.startsWith("/proc/meminfo") || filePath.startsWith("/system/")){
                        return;
                    }
                    cv.put("filePath", filePath);
                }else if(isFileWriteMethod(param, resolvedSinkMethod)){
                    cv.put("type", "fileWrite");
                    String filePath = getFilePathFromFileWriteAPIs(param);
                    if(filePath == null ||  filePath.contains("EdHooker_")){
                        return;
                    }
                    cv.put("filePath", filePath);
                }else if(isWebViewMethod(param, resolvedSinkMethod)) {
                    cv.put("type", "webview");
                    String url = getUrlFromWebViewLoadUrl(param);
                    XposedBridge.log("DCLCallStackLoggingModule: WebView method" + " " + "URL: " + url);

                    if(url != null){
                        cv.put("url", url);
                    }else {
                        String filePath = getFileFromWebViewLoadUrl(param);
                        if(filePath != null){
                            cv.put("filePath", filePath);
                        }
                    }
                    if(param.thisObject != null && param.thisObject instanceof WebView){
                        List<String> interfaceNames = getJavascriptInterfaces((WebView) param.thisObject);
                        if(interfaceNames != null && interfaceNames.size() > 0){
                            cv.put("interfaceNames", Arrays.toString(interfaceNames.toArray()));
                        }
                    }
                }else if(isLoadClassMethod(param, resolvedSinkMethod)) {
                    cv.put("type", "loadClass");
                    String className = getLoadClassName(param);
                    if(className != null){
                        cv.put("loadClassName", className);
                    }
                    //TODO: Actually this one is not working
                }else if(isRenameFileMethod(param, resolvedSinkMethod)) {
                    cv.put("type", "renameFile");
                    String[] filePaths = getFilePathFromRenameFileAPIs(param);
                    XposedBridge.log("DCLCallStackLoggingModule: Rename file: " + filePaths[0] + " " + filePaths[1]);
                    if (filePaths == null || filePaths.length != 2 ||  filePaths[0] == null || filePaths[1] == null || filePaths[0].contains("EdHooker_") || filePaths[1].contains("EdHooker_")) {
                        XposedBridge.log("DCLCallStackLoggingModule: Rename file method, but not a file path");
                        return;
                    }
                    cv.put("filePath", filePaths[0]);
                    cv.put("desFilePath", filePaths[1]);

                }else if(isDeleteFileMethod(param, resolvedSinkMethod)) {
                    cv.put("type", "deleteFile");
                    String filePath = getFilePathFromDeleteFileAPIs(param);

                    if (filePath == null || filePath.contains("EdHooker_")) {
                        return;
                    }
                    cv.put("filePath", filePath);
                    if(param.method instanceof Method){
                        if(((Method) param.method).getReturnType().equals(boolean.class)){
                            param.setResult(true);
                        }else {
                            param.setResult(null);
                        }
                    }
                } else if(isZipInputStreamInitMethod(param, resolvedSinkMethod)) {
                    cv.put("type", "unzip");
                    String filePath = getZipInitFilePathName(param);
                    if (filePath != null) {
                        cv.put("filePath", filePath);
                    }
                } else {
                    XposedBridge.log("Unknown method: " + resolvedSinkMethod.getMethodSignature());
                }
//
                if(cv == null || cv.get("type") == null){
                    return;
                }

                cv.put("stack", getStackJsonString(stack));

                Application app = AndroidAppHelper.currentApplication();
                if(app != null){
                    app.getContentResolver().insert(Uri.parse("content://com.cyfi.autolauncher2.provider/dclresult"), cv);
                }else if(DCLCallStackLoggingModule.this.context != null){
                    DCLCallStackLoggingModule.this.context.getContentResolver().insert(Uri.parse("content://com.cyfi.autolauncher2.provider/dclresult"), cv);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                ResolvedMethod resolvedSinkMethod = null;
                try{
                    if(param.method instanceof Method){
                        resolvedSinkMethod = new ResolvedMethod((Method) param.method);
                    }else if(param.method instanceof Constructor){
                        resolvedSinkMethod = new ResolvedMethod((Constructor) param.method);
                    } else {
                        return;
                    }
                }catch (Exception e){
                    XposedBridge.log("DCLCallStackLoggingModule: " + e.getLocalizedMessage());
                }
                if(resolvedSinkMethod == null) return;

                XposedBridge.log("DCLCallStackLoggingModule: Hooking " + resolvedSinkMethod.declaringClass + " " + resolvedSinkMethod.methodName);
                for(Object arg: param.args){
                    XposedBridge.log("DCLCallStackLoggingModule: arg:  " + arg);
                }
                boolean isHookCall = XposedUtils.isHookCall();
                if(isHookCall){
//                    XposedBridge.log("DCLCallStackLoggingModule: Hook call, caller:" + XposedUtils.getCurrentCaller());
                    return;
                }else {
                    ;
//                    XposedBridge.log("DCLCallStackLoggingModule: Not hook call, caller:" + XposedUtils.getCurrentCaller());
                }
                ContentValues cv = new ContentValues();

                if(isJsonObjectInitMethod(param, resolvedSinkMethod)){
                    if(resolvedSinkMethod.methodName.contains("get")){
                        cv.put("type", "jsonRead");
                    }else{
                        cv.put("type", "jsonInit");
                    }

                    List<String> stack = ThreadCallStackHandler.getInstance().getCallStack(param);
                    cv.put("method", resolvedSinkMethod.getMethodSignature());
                    cv.put("packageName", AndroidAppHelper.currentPackageName());
                    cv.put("stack", getStackJsonString(stack));

                    Object jsonObj = param.thisObject;
                    if(jsonObj != null){
                        cv.put("filePath", "jsonObjHashCode:" + jsonObj.hashCode());
                    }else{
                        cv.put("filePath", "emptynew");
                    }


                    Application app = AndroidAppHelper.currentApplication();
                    if(app != null){
                        app.getContentResolver().insert(Uri.parse("content://com.cyfi.autolauncher2.provider/dclresult"), cv);
                    }else if(DCLCallStackLoggingModule.this.context != null){
                        DCLCallStackLoggingModule.this.context.getContentResolver().insert(Uri.parse("content://com.cyfi.autolauncher2.provider/dclresult"), cv);
                    }
                }





            }


        };

        for(ResolvedMethod rm : this.dclSinkSourceMethods){
            try{
                Class<?> declaringClass = DCLCallStackLoggingModule.this.context.getClassLoader().loadClass(rm.declaringClass);
                if(rm.methodName.equals("<init>")) {
                    XposedBridge.log("DCLCallStackLoggingModule: add Constructor hook for "  + this.packageName +   rm.declaringClass + " " + rm.methodName);
                    XposedBridge.hookAllConstructors(declaringClass, hook);
                }else{
//                    if(rm.methodName.equals("forName") || rm.methodName.equals("findClass")) continue;
                    XposedBridge.log("DCLCallStackLoggingModule: add Method hook for "  + this.packageName + " " +  rm.declaringClass + " " + rm.methodName);
                    XposedBridge.hookAllMethods(declaringClass, rm.methodName, hook);
                }

            }catch(Exception e){
                XposedBridge.log("DCL SOURCE SINK HOOKING ERROR: " + e.getLocalizedMessage());
                e.printStackTrace();

            }
        }
        XposedBridge.log("end Hooking, success DCLCallStackLoggingModule");
    }






    public void hookReflectionInvokeMethod() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!(param.method instanceof Method)) return;
                if(XposedUtils.isHookCall()) return;
                Class<?> declaringClass = (Class<?>) param.thisObject;
                HashSet<String> methodNames = new HashSet<>();


//                Class<?> declaringClass = (Class<?>) param.thisObject;
                if(XposedUtils.isClassInAdditionalIgnore(declaringClass.getName())) return;

                XposedBridge.log("DCLCallStackLoggingModule: Reflection Caller:  " + XposedUtils.getCurrentCaller() + " target Class: " + declaringClass.getName() + " method: " + param.method.getName() + " args: " + param.args.length + " " + (param.args.length == 0 ? " ":  param.args[0]));

                if(param.args.length == 0 && param.getResult() instanceof Method[]) {
                    Method[] methods= (Method[]) param.getResult();
                    if(methods != null){
                        for(Method m: methods){
                            methodNames.add(m.getName());
                        }
                    }
                }else if(param.args.length != 0 && param.args[0] instanceof String){
                    methodNames.add((String) param.args[0]);
                }

                XC_MethodHook reflectionHook = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if(XposedUtils.getCurrentCaller().equals("com.elderdrivers.riru.edxp.yahfa.dexmaker.HookerDexMaker.doMake")) return;
                        if(XposedUtils.getCurrentCaller().equals("java.lang.Class.getMethod")) return;
                        if(XposedUtils.isHookCall()) return;

                        ResolvedMethod resolvedSinkMethod = null;
                        if(param.method instanceof Method){
                            resolvedSinkMethod = new ResolvedMethod((Method) param.method);
                        } else if (param.method instanceof Constructor){
                            resolvedSinkMethod = new ResolvedMethod((Constructor) param.method);
                        } else {
                            return;
                        }
                        if(XposedUtils.isClassInAdditionalIgnore(resolvedSinkMethod.declaringClass)) return;

                        List<String> stack = ThreadCallStackHandler.getInstance().getCallStack(param);
                        XposedBridge.log("DCLCallStackLoggingModule: Reflection method called " + resolvedSinkMethod.declaringClass + " " + resolvedSinkMethod.methodName);


                        ContentValues cv = new ContentValues();
                        ClassLoader loader = XposedUtils.getClassLoaderFromMethod(param.method);
                        if(loader != null) {
                            if(loader instanceof BaseDexClassLoader){
                                List<String> paths = XposedUtils.getDexPathsFromBaseDexClassLoader((BaseDexClassLoader) loader);
                                List<String> validPaths = new ArrayList<>();
                                for(String path: paths){
                                    if(path.startsWith("/system/framework")) continue;
                                    validPaths.add(path);
                                }
                                if(validPaths.size() == 0) return;

                                cv.put("filePath", paths.toString());
                                XposedBridge.log("DCLCallStackLoggingModule: Reflection dex paths" + paths);
                            }else{
                                cv.put("filePath", "invalid/" + loader.getClass().getName());
                                XposedBridge.log("DCLCallStackLoggingModule: Reflection loader is not BaseDexClassLoader");
                                XposedBridge.log(loader.toString());
                            }
                        }else {
                            XposedBridge.log("Empty Loader");
                            cv.put("filePath", "empty/" + resolvedSinkMethod.declaringClass);
                        }
                        cv.put("method", resolvedSinkMethod.getMethodSignature());
                        cv.put("packageName", AndroidAppHelper.currentPackageName());
                        cv.put("type", "reflection");
                        cv.put("stack", getStackJsonString(stack));
                        cv.put("loadClassName", resolvedSinkMethod.declaringClass);
                        cv.put("loadMethodName", resolvedSinkMethod.getMethodSignature());
                        cv.put("isStatic", Modifier.isStatic(param.method.getModifiers()) ? 1 : 0);

                        Context c;
                        if(AndroidAppHelper.currentApplication() != null){
                            c = AndroidAppHelper.currentApplication();
                        }else{
                            c = DCLCallStackLoggingModule.this.context;
                        }
                        if(c != null)
                        {
                            c.getContentResolver().insert(Uri.parse("content://com.cyfi.autolauncher2.provider/dclresult"), cv);
                        }
                    }
                };
                for(String methodName: methodNames){
                    XposedBridge.hookAllMethods(declaringClass, methodName, reflectionHook);
                }
            }
        };

        XposedHelpers.findAndHookMethod(Class.class, "getDeclaredMethod", String.class, Class[].class, hook);
        XposedHelpers.findAndHookMethod(Class.class, "getMethod", String.class, Class[].class, hook);

        XposedHelpers.findAndHookMethod(Class.class, "getDeclaredMethods", hook);
        XposedHelpers.findAndHookMethod(Class.class, "getMethods", hook);
    }

//
//
//
//    public List<String> getCallStack(){
//        StackTraceElement[] stackTraceElements =  Thread.currentThread().getStackTrace();
//        List<String> res =new ArrayList<>();
//        boolean seeHook = false;
//        for(StackTraceElement ste : stackTraceElements){
//            if(ste.getClassName().startsWith("EdHooker_") && ste.getMethodName().equals("hook")) {
//                seeHook = true;
//                continue;
//            }
//            if(seeHook) res.add(ste.getClassName() + ":" + ste.getMethodName() + ":" + ste.getLineNumber());
//        }
//        return res;
//    }

    public String getStackJsonString(List<String> callstack){
        return Arrays.toString(callstack.toArray());
    }


    public boolean isWebViewMethod(XC_MethodHook.MethodHookParam param, ResolvedMethod resolvedSinkMethod){
        return resolvedSinkMethod.declaringClass.equals("android.webkit.WebView");
    }


    public boolean isFileReadMethod(XC_MethodHook.MethodHookParam param, ResolvedMethod resolvedSinkMethod){
        List<String> stringList = Arrays.asList("lines", "newBufferedReader", "readAllLines", "readAllBytes");

        return resolvedSinkMethod.declaringClass.equals("java.util.Scanner") ||
                resolvedSinkMethod.declaringClass.equals("java.io.FileInputStream")  ||
                resolvedSinkMethod.declaringClass.equals("java.io.FileReader") ||
                (resolvedSinkMethod.declaringClass.equals("java.nio.file.Files") && stringList.contains(resolvedSinkMethod.methodName));
    }

    public boolean isFileWriteMethod(XC_MethodHook.MethodHookParam param, ResolvedMethod resolvedSinkMethod){
        return resolvedSinkMethod.declaringClass.equals("java.io.FileOutputStream") ||
                resolvedSinkMethod.declaringClass.equals("java.io.OutputStream") ||
                resolvedSinkMethod.declaringClass.equals("java.io.BufferedWriter") ||
                resolvedSinkMethod.declaringClass.equals("java.io.PrintWriter") ||
                resolvedSinkMethod.declaringClass.equals("java.io.DataOutputStream") ||
                resolvedSinkMethod.declaringClass.equals("java.io.FileWriter")||
                resolvedSinkMethod.declaringClass.equals("java.io.Writer") ||
                (resolvedSinkMethod.declaringClass.equals("java.nio.file.Files") && resolvedSinkMethod.methodName.equals("write")) ;

    }

    public boolean isNetworkMethod(XC_MethodHook.MethodHookParam param, ResolvedMethod resolvedSinkMethod){
        if(resolvedSinkMethod.declaringClass.trim().contains("org.apache.http.impl.client")){
            if(param.args.length == 1) return true;
            else return false;
        }

        return resolvedSinkMethod.declaringClass.equals("java.net.URL") ||
                resolvedSinkMethod.declaringClass.equals("com.android.okhttp.internal.huc.HttpURLConnectionImpl") ||
                resolvedSinkMethod.declaringClass.trim().contains("okhttp3.Response");
    }

    public boolean isLoadClassMethod(XC_MethodHook.MethodHookParam param, ResolvedMethod resolvedSinkMethod){
        return  resolvedSinkMethod.methodName.equals("loadClass") ||
                resolvedSinkMethod.methodName.equals("forName") || resolvedSinkMethod.methodName.equals("findClass");
    }


    public boolean isDeleteFileMethod(XC_MethodHook.MethodHookParam param, ResolvedMethod resolvedMethod){
        return resolvedMethod.methodName.contains("delete");
    }

    public boolean isRenameFileMethod(XC_MethodHook.MethodHookParam param, ResolvedMethod resolvedMethod){
        return resolvedMethod.methodName.contains("renameTo") || resolvedMethod.methodName.contains("copy") || resolvedMethod.methodName.contains("move");
    }

    public boolean isZipInputStreamInitMethod(XC_MethodHook.MethodHookParam param, ResolvedMethod resolvedMethod){
        return resolvedMethod.declaringClass.equals("java.util.zip.ZipInputStream");
    }

    public boolean isJsonObjectInitMethod(XC_MethodHook.MethodHookParam param, ResolvedMethod resolvedMethod){
        return resolvedMethod.declaringClass.equals("org.json.JSONObject");
    }

    public String getFileFromWebViewLoadUrl(XC_MethodHook.MethodHookParam param){
        if(param.method != null && param.method.getName().equals("loadData")){
            return null;
        }

        String url = (String) param.args[0];
        if(url.startsWith("file://")){
            return url.substring(7);
        }
        return null;
    }

    public String getUrlFromNetworkConn(XC_MethodHook.MethodHookParam param){
        Object thisObj = param.thisObject;
        if(param.method.getName().contains("body")){
            XposedBridge.log("DCLCallStackLoggingModule: body method  " + param.thisObject.getClass().getName());
        }

        if(thisObj instanceof URL){
            return ((URL) thisObj).toString();
        }
        else if(thisObj instanceof HttpURLConnection){
            return ((HttpURLConnection) thisObj).getURL().toString();
        }else if(thisObj.getClass().getName().equals("okhttp3.Response")){
            XposedBridge.log("DCLCallStackLoggingModule" +  " URL thisObj: " + XposedUtils.getUrlFromHttp3Response(thisObj));
            return XposedUtils.getUrlFromHttp3Response(thisObj);
        }else if(thisObj.getClass().getName().equals("org.apache.http.impl.client.DefaultHttpClient")){
            XposedBridge.log("DCLCallStackLoggingModule" +  " URL default http client: " + XposedUtils.getUrlFromApacheHttpClientExecute(param.args[0]));
            return XposedUtils.getUrlFromApacheHttpClientExecute(param.args[0]);
        }
        return null;
    }


    public String getUrlFromWebViewLoadUrl(XC_MethodHook.MethodHookParam param){
        String url = null;
        if(param.method != null && param.method.getName().equals("loadData")){
            return null;
        }

        if(param.args.length > 0){
            url = (String) param.args[0];
        }
        return url;
    }

    public String getFilePathFromFileReadAPIs(XC_MethodHook.MethodHookParam param){
        Object possibleFilePathObj;
        if(param.args.length > 0){
            possibleFilePathObj = param.args[0];
            if(possibleFilePathObj instanceof String){
                return (String) possibleFilePathObj;
            }else if(possibleFilePathObj instanceof Path){
                return ((Path) possibleFilePathObj).toAbsolutePath().toString();
            }else if(possibleFilePathObj instanceof File){
                return ((File) possibleFilePathObj).getAbsolutePath();
            }
        }
        return null;
    }


    public String getFilePathFromFileWriteAPIs(XC_MethodHook.MethodHookParam param){
        Object possibleFilePathObj;
        Object outputerObj = param.thisObject;
        if(param.method.getDeclaringClass().getName().equals("java.nio.file.Files")){
            if(param.args.length > 1){
                possibleFilePathObj = param.args[0];
                if(possibleFilePathObj instanceof Path){
                    return ((Path) possibleFilePathObj).toAbsolutePath().toString();
                }else{
                    return null;
                }
            }
        }
        if(outputerObj instanceof FileOutputStream){
            return XposedUtils.getFilePathFromFileOutputStream((FileOutputStream) outputerObj);
        } else if(outputerObj instanceof Writer){
            return XposedUtils.getFilePathFromWriter((Writer) outputerObj);
        }


        if(param.args.length > 0){
            possibleFilePathObj = param.args[0];
            if(possibleFilePathObj instanceof String){
                return (String) possibleFilePathObj;
            }else if(possibleFilePathObj instanceof File){
                return ((File) possibleFilePathObj).getAbsolutePath();
            }
        }
        return null;
    }



    private String getFilePathFromDeleteFileAPIs(XC_MethodHook.MethodHookParam param) {
        Object possibleFilePathObj;
        Object thisObj = param.thisObject;
        if(param.method.getDeclaringClass().getName().equals("java.nio.file.Files")) {
            // first argument is a path
            if (param.args.length >= 1) {
                possibleFilePathObj = param.args[0];
                if (possibleFilePathObj instanceof Path) {
                    XposedBridge.log("DCLCallStackLoggingModule: getFilePathFromDeleteFileAPIs " + ((Path) possibleFilePathObj).toAbsolutePath().toString());

                    return ((Path) possibleFilePathObj).toAbsolutePath().toString();
                } else {
                    return null;
                }
            }
        }else {
            if(thisObj instanceof File){
                return ((File) thisObj).getAbsolutePath();
            }
        }
        return null;
    }

    private String[] getFilePathFromRenameFileAPIs(XC_MethodHook.MethodHookParam param) {
        String[] filePaths = new String[2];

        Object thisObj = param.thisObject;
        XposedBridge.log("DCLCallStackLoggingModule: rename method get file path ");
        if(param.method.getDeclaringClass().getName().equals("java.nio.file.Files")) {
            // first argument is a path
            if (param.args.length > 2) {
                Object possibleSrcPathObj = param.args[0];
                Object possibleDesPathObj = param.args[1];
                if(possibleSrcPathObj instanceof Path && possibleDesPathObj instanceof Path){
                    filePaths[0] = ((Path) possibleSrcPathObj).toAbsolutePath().toString();
                    filePaths[1] = ((Path) possibleDesPathObj).toAbsolutePath().toString();
                    return filePaths;
                }
            }
        }else {
            if(param.method.getDeclaringClass().getName().equals("java.io.File") && thisObj.getClass().getName().equals("java.io.File")) {
                XposedBridge.log("DCLCallStackLoggingModule: rename method get file path in file.renameTo");

                Object desFilePath = param.args[0];
                if (desFilePath instanceof File) {
                    filePaths[0] = ((File) thisObj).getAbsolutePath();
                    filePaths[1] = ((File) desFilePath).getAbsolutePath();
                    return filePaths;
                }
            }

            if(param.method.getDeclaringClass().getName().equals("android.os.FileUtils")){
                if(param.args.length > 2 && param.args[0] instanceof FileDescriptor && param.args[1] instanceof FileDescriptor){
                    FileDescriptor srcFd = (FileDescriptor) param.args[0];
                    FileDescriptor desFd = (FileDescriptor) param.args[1];
                    filePaths[0] = XposedUtils.getFilePathFromDescriptor(srcFd);
                    filePaths[1] = XposedUtils.getFilePathFromDescriptor(desFd);
                    if(filePaths[0] != null && filePaths[1] != null){
                        return filePaths;
                    }
                }
            }
        }
        return null;
    }

    public String getZipInitFilePathName(XC_MethodHook.MethodHookParam param){
        if(param.args.length == 0){
            return null;
        }
        Object a1 = param.args[0];
        XposedBridge.log("DCLCallStackLoggingModule: getZipInitFilePathName " + a1.getClass().getName());

        if(a1 instanceof FilterInputStream){
            String filePath = XposedUtils.getFilePathFromZipInputStream((FilterInputStream) a1);
            return filePath;
        }
        return null;

    }


    public String getLoadClassName(XC_MethodHook.MethodHookParam param){
        if(param.args.length > 0){
            Object possibleClassNameObj = param.args[0];
            if(possibleClassNameObj instanceof String){
                return (String) possibleClassNameObj;
            }
        }
        return null;
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






    public List<String> getJavascriptInterfaces(WebView webView){
        String webViewName = webView.getClass().getName() + "@" + Integer.toHexString(webView.hashCode());
        List<String> res = new ArrayList();
        if(javascriptInterfaces.containsKey(webViewName) && javascriptInterfaces.get(webViewName) != null) {
            for (String interfaceName : javascriptInterfaces.get(webViewName).keySet()) {
                try {
                    res.add(interfaceName + ":" + javascriptInterfaces.get(webViewName).get(interfaceName).getClass().getName());
                } catch (Throwable ignored) {
                }
            }
            return res;
        }else
            return new ArrayList<>();
    }
}
