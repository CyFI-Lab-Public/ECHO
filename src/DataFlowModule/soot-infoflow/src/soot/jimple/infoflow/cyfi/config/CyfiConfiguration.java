package soot.jimple.infoflow.cyfi.config;


import java.util.List;

public class CyfiConfiguration {
    private boolean useJSIEntryPoint = false;

    private String outputFormat = "json";

    private boolean icsProtocalIdentification = false;

    private boolean excludeEntryPointAsSource = false;

    public boolean useFullEntryPoint = false;

    private boolean dynamicLoading = false;

    private boolean logCallMethod = false;

    private boolean noAnalysis = false;

    private boolean logClassHashes = false;

    private String extraEntryPointFilePath = "";


    private String logClassHashesOutputPath = null;

    private String dynamicEvaluationResultPath = null;


    private String jsiInterfaceOfInterestFilePath = null;
    /**
     * Sets the path to files contains serialized clz and method signatures as entrypoints
     *
     * @param useJSIEntryPoint The path to files contains serialized clz and method signatures as entrypoints
     */
    public void setUseJSIEntryPoint(boolean useJSIEntryPoint) {
        this.useJSIEntryPoint = useJSIEntryPoint;
    }

    /**
     * Gets the path to files contains serialized clz and method signatures as entrypoints
     *
     * @return externalEntrypointFilePath The path to files contains serialized clz and method signatures as entrypoints
     */
    public boolean getUseJSIEntryPoint() {
        return this.useJSIEntryPoint;
    }

    public boolean getExcludeEntryPointAsSource() {
        return excludeEntryPointAsSource;
    }

    public void setExcludeEntryPointAsSource(boolean excludeEntryPointAsSource) {
        this.excludeEntryPointAsSource = excludeEntryPointAsSource;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public boolean getLogCallMethod() {
        return logCallMethod;
    }

    public void setLogCallMethod(boolean logCallMethod) {
        this.logCallMethod = logCallMethod;
    }

    public boolean isDynamicLoading() {
        return dynamicLoading;
    }

    public void setDynamicLoading(boolean dynamicLoading) {
        this.dynamicLoading = dynamicLoading;
    }

    public boolean isUseFullEntryPoint() {
        return useFullEntryPoint;
    }

    public void setUseFullEntryPoint(boolean useFullEntryPoint) {
        this.useFullEntryPoint = useFullEntryPoint;
    }


    public boolean isNoAnalysis() {
        return noAnalysis;
    }

    public void setNoAnalysis(boolean noAnalysis) {
        this.noAnalysis = noAnalysis;
    }

    public boolean isLogClassHashes() {
        return logClassHashes;
    }

    public void setLogClassHashes(boolean logClassHashes) {
        this.logClassHashes = logClassHashes;
    }

    public String getLogClassHashesOutputPath() {
        return logClassHashesOutputPath;
    }

    public void setLogClassHashesOutputPath(String logClassHashesOutputPath) {
        this.logClassHashesOutputPath = logClassHashesOutputPath;
    }

    public String getExtraEntryPointFilePath() {
        return extraEntryPointFilePath;
    }

    public void setExtraEntryPointFilePath(String excludeEntryPointFilePath) {
        this.extraEntryPointFilePath = excludeEntryPointFilePath;
    }


    public String getDynamicEvaluationResultPath() {
        return dynamicEvaluationResultPath;
    }

    public void setDynamicEvaluationResultPath(String dynamicEvaluationResultPath) {
        this.dynamicEvaluationResultPath = dynamicEvaluationResultPath;
    }

    public String getJsiInterfaceOfInterestFilePath() {
        return jsiInterfaceOfInterestFilePath;
    }

    public void setJsiInterfaceOfInterestFilePath(String jsiInterfaceOfInterestFilePath) {
        this.jsiInterfaceOfInterestFilePath = jsiInterfaceOfInterestFilePath;
    }

}