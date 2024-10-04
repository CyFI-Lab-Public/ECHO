template = function(){
    var urlToNotifyUser = "<notification_to_users>";
    var shouldNotifyUser = true;

    var packageName = "PKG_NAME";
    var jsiObjectName = "INTERFACE_NAME";
    var jsiExecApiName = "EP_NAME";

    if(shouldNotifyUser){ 
        try{
            eval(jsiObjectName + '.' + jsiExecApiName)(ARG_PATTERN_URL_ONLY)
        }catch(e){
            console.log(e)
        }
    }


    }()