package com.cyfi.XposedPlugin.dynamic.flowdroid;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.cyfi.ENV;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



public class FlowDroidResult {

    public Map<String, SourceMethod> entryPoints;

    public FlowDroidResult() {
        entryPoints = new HashMap<String, SourceMethod>();
    }

    public FlowDroidResult(String jsonFile) throws FileNotFoundException {
      this.getEntryPointsFromJson(jsonFile);
    }

    public void sendValidResultToExposedPlugin(Context context) {
        if(this.entryPoints.size() > 0) {
            Map<String, SourceMethod> entryPoints = getEntryPointsWithValidTaintResult();
                List<String> entryPointMethodNames = new ArrayList<String>(entryPoints.keySet());
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();

            int size = entryPoints.size();
            if(size == 0) return;
            int numOfBatches = (int) Math.ceil(size / 10.0);
            for(int i = 0; i < numOfBatches; i++){
                Map<String, SourceMethod> batch = new HashMap<String, SourceMethod>();
                for(int j = 0; j < 10; j++){
                    int index = i * 10 + j;
                    if(index >= size) break;
                    batch.put(entryPointMethodNames.get(index), entryPoints.get(entryPointMethodNames.get(index)));
                }


                String json = gson.toJson(batch);
                Intent intent = new Intent();
                intent.setAction(ENV.intentPatternToPlugin);
                intent.putExtra("bcType", "FLOWDROID_RESULT");
                intent.putExtra("json", json);
                Log.e("FlowDroidResult","Send FlowDroid Result to Plugin");
                context.sendBroadcast(intent);
            }
        }
    }

    public Map<String, SourceMethod> getEntryPointsWithValidTaintResult(){
        Map<String, SourceMethod> result = new HashMap<String, SourceMethod>();
        for(String key : entryPoints.keySet()){
            SourceMethod sourceMethod = entryPoints.get(key);
            if(sourceMethod != null && !sourceMethod.skip && sourceMethod.getValidTaintResult().size() > 0){
                result.put(key, sourceMethod);
            }
        }
        return result;
    }


    public Type getResultType(){
        return new TypeToken<Map<String, SourceMethod>>(){}.getType();
    }


    public void updateEntryPointFromJsonString(String json){
        if((json == null) || (json.length() == 0)) return;
        if(entryPoints == null) entryPoints = new HashMap<String, SourceMethod>();

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        try{
            Object deserialized = gson.fromJson(json, getResultType());
            if(deserialized instanceof Map) {
                this.entryPoints.putAll((Map<String, SourceMethod>) deserialized);
            }
        } catch(Exception e){
            e.printStackTrace();
        }

    }

    public void getEntryPointsFromJson(String jsonFile) throws FileNotFoundException {
        FileReader fileReader = new FileReader(jsonFile);
        try{
            Object deserialized = new Gson().fromJson(fileReader, getResultType());
            if(deserialized instanceof Map) {
                this.entryPoints = (Map<String, SourceMethod>) deserialized;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }


    public static class Arg{
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

        public boolean hasArgSource(){
            return this.argSources.size() > 0;
        }


    }

    public static class ArgSource{
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

    public static class SinkMethod{
        public String sinkMethodSignature;
        public boolean isStatic;
        public Arg base;
        public List<Arg> args;

        public SinkMethod(String sig){
            this.sinkMethodSignature = sig;
            this.args = new ArrayList<>();
        }

        public boolean hasTaintResult() {
            if (base != null && base.hasArgSource()) return true;
            for (Arg arg : args) {
                if (arg != null && arg.hasArgSource()) return true;
            }
            return false;
        }
    }

    public static class SourceMethod{
        public String sourceMethodSignature;
        public Map<String, SinkMethod> sinkMethods;
        public Set<String> calledMethods;
        public boolean skip;

        public SourceMethod(String sig){
            this.sourceMethodSignature = sig;
            this.sinkMethods = new HashMap<>();
            this.calledMethods = new HashSet<>();
            this.skip = false;
        }

        public Map<String, SinkMethod> getValidTaintResult(){
            Map<String, SinkMethod> result = new HashMap<>();
            for(String key : sinkMethods.keySet()){
                SinkMethod sinkMethod = sinkMethods.get(key);
                if(sinkMethod != null && sinkMethod.hasTaintResult()){
                    result.put(key, sinkMethod);
                }
            }
            return result;
        }

        @NonNull
        public String toString(){
            return this.sourceMethodSignature;
        }
    }
}

