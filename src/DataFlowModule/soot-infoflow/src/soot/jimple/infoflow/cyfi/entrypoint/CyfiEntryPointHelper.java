package soot.jimple.infoflow.cyfi.entrypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.AnnotationTag;
import soot.tagkit.VisibilityAnnotationTag;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class CyfiEntryPointHelper {

    private static final CyfiEntryPointHelper instance = new CyfiEntryPointHelper();

    private List<String> extraEntryPointList;
    private List<SootMethod> extraEntryPointMethods;

    private Set<String> jsiInterfaceOfInterestList;


    private CyfiEntryPointHelper() {
        this.extraEntryPointList = new ArrayList<>();
        this.extraEntryPointMethods = new ArrayList<>();
        this.jsiInterfaceOfInterestList = new HashSet<>();
    }



    public static CyfiEntryPointHelper v() {
        return instance;
    }

    public Set<SootClass> getJSIEntryPointClasses(){
        Set<SootClass> jsiEntryPointClass = new HashSet<>();
        for(SootClass clz: Scene.v().getClasses()){

            if(!this.jsiInterfaceOfInterestList.isEmpty() && !this.jsiInterfaceOfInterestList.contains(clz.getName())){
                continue;
            }

            if(clz.isConcrete()  && !clz.isLibraryClass() && !getJSIEntrypoints(clz).isEmpty()){
                System.out.println(clz.toString());
                System.out.println(clz.isApplicationClass());
                jsiEntryPointClass.add(clz);
            }
        }
        return jsiEntryPointClass;
    }

    public HashSet<SootMethod> getJSIEntrypoints(SootClass clz){
        HashSet<SootMethod> sms = new HashSet<>();
        for(SootMethod sm: clz.getMethods()){
            VisibilityAnnotationTag tag = (VisibilityAnnotationTag) sm.getTag("VisibilityAnnotationTag");
            boolean hasJsInterface = false;
            if(tag != null){
                for(AnnotationTag anno: tag.getAnnotations()){
                    if(anno.getType().equals("Landroid/webkit/JavascriptInterface;")){
                        hasJsInterface = true;
                        break;
                    }
                }
            }
            if(hasJsInterface) sms.add(sm);
        }
        return sms;
    }

    public void initExtraClassList(String excludeEntryPointFilePath) throws IOException {
        this.extraEntryPointList.clear();
        try{
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            Reader reader = Files.newBufferedReader(Paths.get(excludeEntryPointFilePath));
            List<String> excludeEntryPointList = gson.fromJson(reader, List.class);
            this.extraEntryPointList.addAll(excludeEntryPointList);

            this.extraEntryPointMethods.clear();
            for(String s: this.extraEntryPointList){
                try{
                    SootMethod sm = Scene.v().getMethod(s);
                    if(sm != null){
                        this.extraEntryPointMethods.add(sm);
                    }
                }catch(Exception ignored){
                }
            }
        } catch (IOException ignored) {
            ;
        }
    }

    public void initJsiInterfaceOfInterestList(String jsiInterfaceOfInterestFilePath) throws IOException {
        this.jsiInterfaceOfInterestList.clear();
        try{
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            Reader reader = Files.newBufferedReader(Paths.get(jsiInterfaceOfInterestFilePath));
            List<String> jsiInterfaceOfInterestList = gson.fromJson(reader, List.class);
            this.jsiInterfaceOfInterestList.addAll(jsiInterfaceOfInterestList);
        } catch (IOException ignored) {
            ;
        }
    }


    public List<String> getExtraEntryPointList(){
        if(extraEntryPointList == null) return new ArrayList<>();
        else {
            return extraEntryPointList;
        }

    }

    public List<SootMethod> getExtraEntryPointMethods(){
        return extraEntryPointMethods;
    }

    public boolean isExcludedEntryPointClz(SootClass sootClass){
        return this.extraEntryPointList.contains(sootClass.getName());
    }



}
