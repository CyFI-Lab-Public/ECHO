package soot.jimple.infoflow.cyfi.entrypoint;


import java.util.ArrayList;
import java.util.List;

public class DCLResult {
    public String method;
    public String packageName;
    public String type;
    public String url;
    public String filePath;
    public String loadClassName;
    public String loadMethodName;
    public List<String> stack;
    public boolean isStatic;
    public List<String> interfaceNames;
    public String desFilePath;


    public DCLResult(String method, String packageName, String type, String url, String filePath, String loadClassName, String loadMethodName, String stack, boolean isStatic, String interfaceNames, String desFilePath) {
        this.method = method;
        this.packageName = packageName;
        this.type = type;
        this.url = url;
        this.filePath = filePath;
        this.loadClassName = loadClassName;
        this.loadMethodName = loadMethodName;
        this.stack = getListFromString(stack);
        this.isStatic = isStatic;
        this.interfaceNames = getListFromString(interfaceNames);
        this.desFilePath = desFilePath;
    }

    public DCLResult(String method, String packageName, String type, String url, String filePath, String loadClassName, String loadMethodName, List<String> stack, boolean isStatic, List<String> interfaceNames, String desFilePath) {
        this.method = method;
        this.packageName = packageName;
        this.type = type;
        this.url = url;
        this.filePath = filePath;
        this.loadClassName = loadClassName;
        this.loadMethodName = loadMethodName;
        this.stack = stack;
        this.isStatic = isStatic;
        this.interfaceNames = interfaceNames;
        this.desFilePath = desFilePath;
    }




    public static List<String> getListFromString(String str){
        if(str == null || str.length() == 0) return null;
        List<String> list = new ArrayList<>();
        String[] strArray = str.substring(1, str.length()-1).split(",");
        for (String strSeg : strArray){
            list.add(strSeg.trim());
        }
        if(strArray.length == 0) return null;
        return list;
    }

}
