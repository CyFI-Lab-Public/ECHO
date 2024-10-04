package soot.jimple.infoflow.cyfi.classmethod;

import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import soot.jimple.infoflow.cyfi.infoflow.CyfiInfoflowHelper;
import soot.jimple.infoflow.util.SystemClassHandler;

public class CyfiClassMethodHelper {
    public static List<LoggingClass> getAllClasses() {
        List<LoggingClass> classes = new java.util.ArrayList<>();
        List<SootClass> libclass = new ArrayList<>();
        List<SootClass> appClz = new ArrayList<>() ;
        List<SootClass> otherClz = new ArrayList<>();
        for (SootClass sootClass : Scene.v().getClasses()) {
            if(CyfiInfoflowHelper.v().isClassInSupportPackage(sootClass) || SystemClassHandler.v().isClassInSystemPackage(sootClass) || CyfiInfoflowHelper.v().isClassInAdditionalIgnore(sootClass.getName())){
                continue;
            }
            LoggingClass loggingClass = new LoggingClass(sootClass);
            if(loggingClass.isEmptyClz()){
                continue;
            }

            classes.add(new LoggingClass(sootClass));
//            if(SystemClassHandler.v().isClassInSystemPackage(sootClass)){
//                appClz.add(sootClass);
//            }
//            else if(CyfiInfoflowHelper.v().isClassInSupportPackage(sootClass.getName())){
//                libclass.add(sootClass);
//            }else{
//                otherClz.add(sootClass);
//            }
        }
        return classes;
    }

    public static void logClasses(String outputFilePath) throws IOException {
//        tmp();
        List<LoggingClass> classes = getAllClasses();
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        FileWriter writer = null;
        try {
            writer = new FileWriter(outputFilePath);
            gson.toJson(classes, writer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                } else {
                    System.out.println("Buffer has not been initialized!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static int classHashCode(SootClass sootClass) {
        int h = sootClass.getName().hashCode();
        for(SootField field: sootClass.getFields()) {
            h = h * 31 + field.getName().hashCode();
        }

        for(SootMethod m: sootClass.getMethods()){
            h = h * 31 + methodHashCode(m);
        }

        for(SootField sootField:sootClass.getFields()){
            h = h * 31 + sootField.getSignature().hashCode();
        }

        return h;
    }

    public static int methodHashCode(SootMethod sootMethod){
        int h = sootMethod.getName().hashCode();
        try{
            // load method body if it is not loaded yet
            if(!sootMethod.hasActiveBody()){
                sootMethod.retrieveActiveBody();
            }
        } catch(Exception ignored){
        }

        if(sootMethod.hasActiveBody()){
            for(Unit u: sootMethod.getActiveBody().getUnits()){
                h = h * 31 + u.toString().hashCode();
            }
        }else{
            h = h * 31 + sootMethod.getSignature().hashCode();
        }

        return h;
    }

    public static void tmp(){
        List<SootClass> classLoaders = new ArrayList<>();
        for(SootClass sootClass: Scene.v().getClasses()){
            if(sootClass.getName().contains("java.io.File") ){
                classLoaders.add(sootClass);
            }
        }

        for(SootClass sootClass: classLoaders){
            System.out.println(sootClass.getName());
            List<SootMethod> methods = sootClass.getMethods();
            for(SootMethod sootMethod: methods){
                if(sootMethod.getName().contains("delete"))
                    System.out.println(sootMethod.getSignature());
            }
        }
        System.out.println("===================================");
    }


}
