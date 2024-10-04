package com.cyfi.XposedPlugin.dynamic.flowdroid;

import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XposedBridge;

public  class ResolvedMethod {
    public String declaringClass;
    public String methodName;
    public String methodSignature;
    public String[] argTypes;
    public String returnType;
    public String sinkSourcetype;


    public ResolvedMethod(String methodSignature) {
        this.methodSignature = methodSignature.split("->")[0].trim();
        if(methodSignature.length() > 0){

            if(methodSignature.contains("->")){
                try{
                    this.sinkSourcetype = methodSignature.split("->")[1].trim();
                } catch(Exception ignored){
                }
            }

            try{
                String cleared = methodSignature.split("->")[0].trim();
                cleared = cleared.substring(1, cleared.length() - 1);

                String[] parts = cleared.split(":");
                this.declaringClass = parts[0];
//                    XposedBridge.log("declaringClass: " + declaringClass);

                String rest = parts[1].trim();
                parts = rest.split(" ");
                this.returnType = parts[0];
//                    XposedBridge.log("returnType: " + returnType);

                this.methodName = parts[1].trim().split("\\(")[0];
//                    XposedBridge.log("methodName: " + methodName);
                rest = parts[1].trim().split("\\(")[1];
                argTypes = rest.replace(')', ' ').trim().split(",");
//                    XposedBridge.log("argTypes: " + Arrays.toString(argTypes));
                if(!this.methodSignature.equals(getMethodSignature())){
                    Log.e("ResolvedMethod Exposed", "Method signature mismatch: " + this.methodSignature + " vs " + getMethodSignature());
                };

            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public ResolvedMethod(Method method){
        this.declaringClass = method.getDeclaringClass().getName();
        this.returnType = method.getReturnType().getName();
        this.methodName = method.getName();
        this.argTypes = new String[method.getParameterTypes().length];
        for(int i = 0; i < argTypes.length; i++){
            argTypes[i] = method.getParameterTypes()[i].getName();
        }
        this.methodSignature = getMethodSignature();
    }

    public ResolvedMethod(Constructor constructor){
        this.declaringClass = constructor.getDeclaringClass().getName();
        this.returnType = constructor.getDeclaringClass().getName();
        this.methodName = "<init>";
        this.argTypes = new String[constructor.getParameterTypes().length];
        for(int i = 0; i < argTypes.length; i++){
            argTypes[i] = constructor.getParameterTypes()[i].getName();
        }
        this.methodSignature = getMethodSignature();

    }

    public String getMethodSignature() {
        return "<" + declaringClass + ": " + returnType + " " + methodName + "(" + TextUtils.join(",", argTypes) + ")>";
    }

}