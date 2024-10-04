package com.cyfi.controller.fileMonitor;

import android.os.Environment;

import com.cyfi.XposedPlugin.dynamic.control.ControlUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class FileMonitor {
    private static FileMonitor instance;
    private Set<String> lastResult;

    public FileMonitor(){
    }

    public Set<String> getCorrentFileSnap(File file){
        Set<String> results = new HashSet<>();
        if(file == null){
            file = Environment.getExternalStorageDirectory();
        }
        File[] files = file.listFiles();
        if(files == null){
            return results;
        }
        for(File f: file.listFiles()){
            if(f.isDirectory()) {
                results.addAll(getCorrentFileSnap(f));
            }
            else{
                results.add(f.getAbsolutePath());
            }

        }
        return results;
    }

    public void initFileSnaps(){
        this.lastResult = getCorrentFileSnap(null);
    }

    public Set<String> getNewFileList(){
        Set<String> newResult = getCorrentFileSnap(null);
        newResult.removeAll(this.lastResult);
        return newResult;
    }

    public void logDifferentFiles(String packageName) throws IOException {
        String fileName = "File_" + packageName + ".txt";
        Set<String> results = getNewFileList();

        for (String s: results){
            ControlUtils.writeSDFile(fileName, s);
        }



    }



     public static FileMonitor getInstance() {
        if(instance == null) {
            instance = new FileMonitor();
        }
        return instance ;
    }
}
