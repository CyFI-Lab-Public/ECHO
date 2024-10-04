package soot.jimple.infoflow.cyfi.entrypoint;

import com.google.gson.Gson;
import org.checkerframework.checker.units.qual.A;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class DCLSummaryObject {
    public List<DCLResult> fileWrite;
    public List<DCLResult> reflection;
    public List<DCLResult> network;
    public List<DCLResult> rename;
    public List<DCLResult> delete;

    public DCLSummaryObject(){
        this.fileWrite = new ArrayList<>();
        this.reflection = new ArrayList<>();
        this.network = new ArrayList<>();
        this.rename = new ArrayList<>();
        this.delete = new ArrayList<>();

    }

    public static DCLSummaryObject parseJsonFile(String jsonFilePath) throws IOException {
        Gson gson = new Gson();

        Reader reader = Files.newBufferedReader(Paths.get(jsonFilePath));

        return gson.fromJson(reader, DCLSummaryObject.class);
    }
}
