package com.cyfi;

public class ENV {
    public static String malwareFolder = "/sdcard/";
    public static String malwareName = "malware.apk";
    public static String malwarePath = malwareFolder + malwareName;

    public static String proxyUrl = "http://cyfi.deviceregister.com";

    public static String dclEvaluationUrl =  "http://192.168.2.118:5000";

    public static String dclValidUrl = "http://192.168.2.118:5002";

    public static String[] dclEvaluationList= new String[] {"895X05PAJ", "8CJX1PHL0", "953X200J9", "952X1ZYME", "953X2006F"};

    //public static String[] dclValidDeviceList = new String[]{"952X1ZYME", "953X2006F", "953X200YH"};

    public static String intentPatternFromPlugin = "com.cyfi.broadcast.from.plugin";
    public static String intentPatternToPlugin = "com.cyfi.broadcast.to.plugin";
    public static String intentPatternFileLogging = "com.cyfi.broadcast.file";

    public static int fuzzingTestTimes = 3;

    public static String serverUrlFile = "/data/local/tmp/serverUrl.json";
}
