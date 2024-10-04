package com.cyfi.XposedPlugin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.RequiresApi;

import com.cyfi.ENV;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipInputStream;

import dalvik.system.BaseDexClassLoader;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class XposedUtils {
    public static boolean isPackageIgnored(String packageName){
        if(packageName.equals("com.android.chrome"))
        {
            return true;
        }

        if(packageName.startsWith("com.android")) {
            return true;
        }
        if(packageName.equals("android")) return true;
        if(packageName.startsWith("com.google")){
            return true;
        }
        if(packageName.startsWith("com.qualcomm")){
            return true;
        }
        if(packageName.startsWith("com.qti.qualcomm")){
            return true;
        }



        ArrayList<String> arrayList = new ArrayList<>(Arrays.asList("com.cyfi.autolauncher2", "com.cyfi.xposedplugin.cyfidclxposedplugin","eu.chainfire.supersu", "system", "me.twrp.twrpapp"
                ,"de.robv.android.xposed.installer", "org.codeaurora.ims","com.breel.wallpapers18","com.quicinc.cne.CNEService","com.topjohnwu.magisk", "com.cyfi.newautolauncher", "org.meowcat.edxposed.manager"));

        return arrayList.contains(packageName);
    }


    public static void writeSDFile(String fileName, String write_str) throws IOException {
            Intent intent = new Intent();
            intent.setAction(ENV.intentPatternFileLogging);
            intent.putExtra("path", fileName);
            intent.putExtra("content", write_str);

            Context context = (Context) AndroidAppHelper.currentApplication();
            context.sendBroadcast(intent);
     //writeSDFile
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String getFilePathFromDescriptor(FileDescriptor fd){
        try{
            Class fdClz = fd.getClass();
            Field descriptorField = fdClz.getDeclaredField("descriptor");
            descriptorField.setAccessible(true);
            int descriptor = descriptorField.getInt(fd);
            XposedBridge.log("try to get file path for descriptor: " + descriptor);
            Path path = Paths.get("/proc/self/fd/" + String.valueOf(descriptor));
            return Files.readSymbolicLink(path).toString();
        }catch (Exception e){
            System.out.println(Arrays.toString(e.getStackTrace()));
            return null;
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String getFilePathFromFileOutputStream(FileOutputStream fileOutputStream) {
        try{
            Class<?> fdClz = FileOutputStream.class;
            Field descriptorField = fdClz.getDeclaredField("fd");
            descriptorField.setAccessible(true);
            FileDescriptor fd = (FileDescriptor) descriptorField.get(fileOutputStream);
            String filePath = getFilePathFromDescriptor(fd);
            return filePath;

        }catch(Exception e){
            System.out.println(Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static String getFilePathFromWriter(Writer writer){

        try{
            Class<?> fdClz = Writer.class;

            Field lockField = fdClz.getDeclaredField("lock");
            lockField.setAccessible(true);

            Object lockObj = lockField.get(writer);
            if(lockObj == null) return null;
            else if(lockObj instanceof Writer){
                XposedBridge.log("Writer: lock : " + lockObj.getClass().getName());
                if(lockObj == writer) return null;
                return getFilePathFromWriter((Writer) lockObj);
            } else if(lockObj instanceof FileOutputStream){
                return getFilePathFromFileOutputStream((FileOutputStream) lockObj);
            } else {
                return null;
            }
        }
        catch (Exception e){
            System.out.println(Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    public static String getFilePathFromZipInputStream(FilterInputStream zis){
        try {
            InputStream in = getInFromInputStream(zis);
            while (in != null && (in instanceof FilterInputStream)){
                in = getInFromInputStream((FilterInputStream) in);
                XposedBridge.log("ZipInputStream: in : " + in.getClass().getName());
            }
            if (in instanceof FileInputStream) {

                FileDescriptor fd = ((FileInputStream) in).getFD();
                XposedBridge.log("ZipInputStream: fd : " + fd.getClass().getName());

                String filePath = getFilePathFromDescriptor(fd);
                XposedBridge.log("ZipInputStream: filePath : " + filePath);

                return filePath;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public static InputStream getInFromInputStream(FilterInputStream inputStream){
        try {
            Class clazz = Class.forName("java.io.FilterInputStream");
            Field field = clazz.getDeclaredField("in");
            field.setAccessible(true);
            Object obj = field.get(inputStream);
            if (obj instanceof InputStream){
                XposedBridge.log("getInFromInputStream: " + obj.getClass().getName());
                return (InputStream) obj;
            }

            XposedBridge.log("getInFromInputStream: null at middle, " + obj.getClass().getName());
            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        XposedBridge.log("getInFromInputStream: null at end");
        return null;


    }


    public static  boolean isClassInAdditionalIgnore(String className){
//        String[] ignoredClasses = new String[]{"int", "byte", "char", "double", "float", "short", "long", "void", "boolean"};
        if(className.startsWith("EdHooker_")) return true;
        String[] ignoredPrefixes = new String[]{"android.", "java.", "javax.", "sun.", "org.omg.", "org.w3c.dom.", "com.google.",
                "com.android.", "androidx.", "android.support.", "java.util.", "dalvik.", "libcore.",
                "org.apache.", "org.xml.", "org.ccil.", "org.json.", "org.xmlpull.", "com.sun.", "org.kxml2.io.", "junit.framework.Assert.",
                "com.elderdrivers.riru", "de.robv.android.xposed", "external.com.android.dx"};
//        for(String clz: ignoredClasses){
//            if(className.equals(clz)) return true;
//        }
        for(String prefix: ignoredPrefixes){
            if(className.startsWith(prefix)){
                return true;
            }
        }
        return false;
    }



    public static ClassLoader getClassLoaderFromMethod(Member method){
        try{
            Class clz = method.getDeclaringClass();
            Field classLoaderField = Class.class.getDeclaredField("classLoader");
            classLoaderField.setAccessible(true);
            return (ClassLoader) classLoaderField.get(clz);
        }catch(Exception e){
            XposedBridge.log(e.getLocalizedMessage());
            return null;
        }
    }


    public static List<String> getDexPathsFromBaseDexClassLoader(BaseDexClassLoader baseDexClassLoader) {

        List<String> res = new ArrayList<>();
        if(baseDexClassLoader == null) {
            return res;
        }
        try{
            Field pathListField = BaseDexClassLoader.class.getDeclaredField("pathList");
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(baseDexClassLoader);
            System.out.println(pathList);
            Field dexElementsField = pathList.getClass().getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object[] dexElements = (Object[]) dexElementsField.get(pathList);
            System.out.println(dexElements);
            Class cz = dexElements.getClass();
            Class superClz = cz.getSuperclass();
            if(dexElements.length > 0){
                Field dexFileField = dexElements[0].getClass().getDeclaredField("dexFile");
                dexFileField.setAccessible(true);
                Field dexPathField = dexElements[0].getClass().getDeclaredField("path");
                dexPathField.setAccessible(true);

                for(Object dexElement : dexElements){
                    Object dexFile =  dexFileField.get(dexElement);
                    if(dexFile != null) {
                        Field mPathField = dexFile.getClass().getDeclaredField("mFileName");
                        mPathField.setAccessible(true);
                        String path = (String) mPathField.get(dexFile);
                        res.add(path);
                    }else{
                        File path = (File) dexPathField.get(dexElement);
                        res.add(path.getAbsolutePath());
                    }
                }
            }
        }catch (Exception e){
        }
        return res;
    }

    public static Field getFieldFromClassOrParent(Class clz, String fieldName){
        try{
            Field field = clz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        }catch (Exception e){
            Class parentClz = clz.getSuperclass();
            if(parentClz != null){
                return getFieldFromClassOrParent(parentClz, fieldName);
            }else{
                return null;
            }
        }
    }


    public static String getUrlFromHttp3Response(Object responseObj){
        try{
            if(responseObj == null || !responseObj.getClass().getName().equals("okhttp3.Response")) return null;
            Field field = responseObj.getClass().getDeclaredField("request");
            field.setAccessible(true);
            Object request1 = field.get(responseObj);
            Field urlField = request1.getClass().getDeclaredField("url");
            urlField.setAccessible(true);
            Object url = urlField.get(request1);
            String urlStr = url.toString();
            System.out.println(urlStr);
            return urlStr;

        } catch (Exception e){
            System.out.println(Arrays.toString(e.getStackTrace()));
            return null;
        }
    }

    public static String getUrlFromApacheHttpClientExecute(Object UriRequestObject){
        try{
            if(UriRequestObject == null){
                return null;
            }
            String name = UriRequestObject.getClass().getName();
            if(UriRequestObject.getClass().getName().equals("org.apache.http.client.methods.HttpGet") ||
                    UriRequestObject.getClass().getName().equals("org.apache.http.client.methods.HttpPost") ||
                    UriRequestObject.getClass().getName().equals("org.apache.http.client.methods.HttpPut") ||
                    UriRequestObject.getClass().getName().equals("org.apache.http.client.methods.HttpDelete") ||
                    UriRequestObject.getClass().getName().equals("org.apache.http.client.methods.HttpHead") ||
                    UriRequestObject.getClass().getName().equals("org.apache.http.client.methods.HttpOptions") ||
                    UriRequestObject.getClass().getName().equals("org.apache.http.client.methods.HttpTrace") ||
                    UriRequestObject.getClass().getName().equals("org.apache.http.client.methods.HttpPatch")
            ){
                Method method = UriRequestObject.getClass().getSuperclass().getDeclaredMethod("getURI");
                method.setAccessible(true);
                Object uriObject = method.invoke(UriRequestObject);
                if(uriObject == null){
                    return null;
                }
                return uriObject.toString();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }



    public static boolean isHookCall(){
        String currentCaller = getCurrentCaller();
        if(currentCaller == null){
            return true;
        }
        if(currentCaller.contains("hookSinkMethods") || currentCaller.contains("beforeHookedMethod") || currentCaller.contains("afterHookedMethod")) return true;

        if(currentCaller.startsWith("com.cyfi") || currentCaller.startsWith("de.robv.android.xposed") ||
                currentCaller.startsWith("androidx.appcompat.widget") || currentCaller.startsWith("com.elderdrivers.riru")){

            return true;
        }

        if(currentCaller.startsWith("java.lang.Class") || currentCaller.startsWith("dalvik.system") ||
                currentCaller.startsWith("java.lang.ClassLoader") || currentCaller.startsWith("external.com.android.dx"))
        {
            return true;
        }


        XposedBridge.log("hook should be processed");
        return false;
    }

    public static String getCurrentCaller(){
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean seeHook = false;
        int count = 0;
        for(StackTraceElement ste: stack){
            if(ste.getClassName().equals("com.cyfi.XposedPlugin.Modules.DCLCallStackLoggingModule$1") && ste.getMethodName().equals("beforeHookedMethod")){
                count ++;
                if(count >10) return ste.getClassName() + "." + ste.getMethodName();
            }

            if(ste.getClassName().startsWith("EdHooker_") && ste.getMethodName().equals("hook")) {
                seeHook = true;
                continue;
            }

            if(ste.getClassName().startsWith("java.lang.reflect")) continue;

            if(seeHook){
                return ste.getClassName() + "." + ste.getMethodName();
            }
        }

        return "hookSinkMethods";
    }



}
