package com.cyfi.XposedPlugin.Modules.webviewfuzzing;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;

public class FuzzArg
{
    public Object value;
    public Type type;
    public boolean isArray;
    public String identifier;
    public FuzzArg(final Type type) {
        this.type = type;
        identifier = Integer.toHexString(this.hashCode());
        this.value = generateValueByType();
    }


    public  String toString(){
        if(this.value != null){
            return value.toString();
        }else{
            return "null, type: " + type.toString();
        }
    }

    Object generateValueByType(){
        switch(type.toString()){
            case "class java.lang.String":
                if(isArray){
                    return Collections.singletonList(identifier).toArray();
                }else
                    return identifier;


            case "byte":
            case "class java.lang.Byte":
                if(isArray){
                    return (identifier).getBytes();
                } else
                    return (byte)1;

            case "class java.lang.Integer":
            case "int":
            case "class java.lang.Long":
            case "long":
                if (isArray) {
                    return Collections.singletonList(this.hashCode()).toArray();
                }else
                    return this.hashCode();
            case "class java.lang.Boolean":
            case "boolean":
                if (isArray) {
                    return Collections.singletonList(true).toArray();
                }else
                    return true;
            case "java.lang.Double":
            case "double":
            case "class java.lang.Float":
            case "float":
                if (isArray) {
                    return Collections.singletonList((double) this.hashCode()).toArray();
                }
                else return (double)this.hashCode();
            default:
                return null;
        }
    }
}
