'use strict';




const fs = require('frida-fs');

function isSystemClass(className) {
    var exclude_prefix = ["android.", "java.", "javax.", "sun.", "org.omg.", "org.w3c.dom.", "com.google.",
        "com.android.", "androidx.", "android.support.", "java.util.", "dalvik.", "libcore.",
        "org.apache.", "org.xml.", "org.ccil.", "org.json.", "org.xmlpull.", "com.sun.", "org.kxml2.io.", "junit.framework.Assert",
        "int", "byte", "char", "double", "float", "short", "long", "void", "boolean",
        "com.elderdrivers.riru", "de.robv.android.xposed", "external.com.android.dx", "org.chromium."];
    for (var i = 0; i < exclude_prefix.length; i++) {
        if (className.startsWith(exclude_prefix[i])) {
            return true;
        }
    }
    return false;
}
// get all plain text classes from classloader
var existClasses;

try {
    var text = fs.readFileSync("/data/data/#package_name#/classes.json");
    var fileContent = JSON.parse(text);
    existClasses = fileContent.map(o => o.className);
} catch (e) {
    console.log(e);
    send(e);
    existClasses = [];
}
const hookedTargetClasses = [];
const hookedTargetCLassNames = [];


// send("exist_classes: " + existClasses);
function isMethodInitFromPlain(stackClassList, currentClassName) {
    var count = 0;
    for (let i = 0; i < stackClassList.length; i++) {
        const stackClass = stackClassList[i];
        if (isSystemClass(stackClass)) {
            continue;
        } else if (existClasses.includes(stackClass)) {
            return true;
        } else if (hookedTargetCLassNames.includes(stackClass)) {
            if (stackClass == currentClassName && count == 0) {
                count += 1;
                continue;
            } else {
                return false;
            }
        }
    }
    return false;

}


function numberOfClassLoader() {
    var count = 0;
    var classLoaders = Java.enumerateClassLoadersSync();
    return classLoaders.length;

}

function loadClass(className) {
    var targetClasses = [];
    var classFactory;

    var classLoaders = Java.enumerateClassLoadersSync();
    for (const classLoader in classLoaders) {
        try {
            classLoaders[classLoader].findClass(className);
            classFactory = Java.ClassFactory.get(classLoaders[classLoader]);
            targetClasses.push(classFactory.use(className));
        } catch (e) {
            // console.log( e);
            continue;
        }
    }

    return targetClasses;
}



function hookAllDeclaredMethodForClass(cls) {
    // if(!cls.class.getName().startsWith('com.cyfi.dynamic.Dynamic')) return;
    //send('hooking class: ' + cls.class.getName());
    const mhd_array = cls.class.getDeclaredMethods();

    // hook 类所有方法 （所有重载方法也要hook)
    for (var i = 0; i < mhd_array.length; i++) {
        // 当前方法签名
        const mhd_cur = mhd_array[i];
        // 当前方法名
        const str_mhd_name = mhd_cur.getName();
        
        // 当前方法重载方法的个数
        const mhd = cls[str_mhd_name];
        if(mhd == undefined) continue;

        const n_overload_cnt = mhd.overloads.length;
        // console.log('hooking method: ' + str_mhd_name + " " + n_overload_cnt);
        // Java.use("android.util.Log").d("Frida", "hooking method: " + str_mhd_name);
        for (var index = 0; index < n_overload_cnt; index++) {
            var argTypes = cls[str_mhd_name].overloads[index].argumentTypes;
            var returnType = cls[str_mhd_name].overloads[index].returnType;
            const sig = getMethodSignature(cls.class.getName(), returnType, 
            str_mhd_name, argTypes);

            //console.log(sig);
            cls[str_mhd_name].overloads[index].implementation = function () {
                // 参数个数
                var n_arg_cnt = arguments.length;
                // Java.use("android.util.Log").d("Frida", "method call: " + str_mhd_name);

                const traceClasses = stackTrace(false);
                if (isMethodInitFromPlain(traceClasses, cls.class.getName())) {
                    send({ "type": "bridge",
                        "bridge": sig});
                }
                // send(str_mhd_name + ' --- ' + n_arg_cnt + "----" + sig);
                return this[str_mhd_name].apply(this, arguments);
                // return this[str_mhd_name].apply(this, arguments);
            }
        }
    }
}


var modules = Process.enumerateModules();
const target_exports = [];
modules.forEach(element => {
    if(element.name == 'libart.so'){
        const m = element;
        m.enumerateExports().forEach(function (e) {
            //console.log(e.name);
            if(e.name.includes("OpenCommon")){
                console.log(e.name);
                target_exports.push(e.name);
            }
        });
    }
});

// _ZN3art16ArtDexFileLoader10OpenCommonEPKhjS2_jRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_NS3_10unique_ptrINS_16DexFileContainerENS3_14default_deleteISH_EEEEPNS_13DexFileLoader12VerifyResultE
// _ZN3art13DexFileLoader10OpenCommonEPKhmS2_mRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPKNS_10OatDexFileEbbPS9_NS3_10unique_ptrINS_16DexFileContainerENS3_14default_deleteISH_EEEEPNS0_12VerifyResultE

target_exports.forEach(target_export => {
    try {
        Interceptor.attach(Module.findExportByName("libart.so", target_export), {
            onEnter: function (args) {
                var message = {}
                // console.log(args[0])
                var begin = this.context.x0
                // console.log(this.context.isNull())
                if (begin == undefined){
                    begin = args[1]
                }
                message.type='payload_file'
                message.base = parseInt(begin, 16)

                // get 4 begining byte hex of the file
                var magic = Memory.readUtf8String(begin)
                message.magic = magic
                message.arg_file_size = args[2].toInt32()

                // if is jar file
                if (magic.startsWith('PK')) {
                    // get file size from arg
                    // var size = Memory.readU32(begin.add(0x1c))
                    message.file_type = 'zip'
                    message.file_size = args[2].toInt32()
                    message.file_name = '#package_name#_' + message.file_size + '.zip'; 

                }else if (magic.startsWith('dex')) { // if is dex file
                    message.file_type = 'dex'
                    var address = parseInt(begin, 16) + 0x20
                    message.file_size = Memory.readInt(ptr(address))
                    message.file_name = '#package_name#_' + message.file_size + '.dex'; 
                    // console.log("magic : " + Memory.readUtf8String(begin))
                    // dex.dex_type = Memory.readUtf8String(begin)
                }else{
                    message.error = 'unknown_file_type'
                    send(message)
                    return;
                }
                    // console.log("dex_size: " + message.file_size + ', type: ' + message.file_type + ' , magic: ' + magic) 

                    // dex.file_path = "/data/data/#package_name#/" + dex.dex_size + ".dex";
                    // var file = new File(dex.file_path, "wb")
                    // file.write(Memory.readByteArray(begin, dex.dex_size))
                    // file.flush()
                    // file.close()
                send(message, Memory.readByteArray(begin, message.file_size));
            },
            onLeave: function (retval) {
            }
        });
    } catch (error) {
        console.log(error);    
    }

});


function searchAndHookNewClassMethod(clz) {
    var className = convertSmailType(clz);
    if ((!isSystemClass(className) && !existClasses.includes(className))) {
        const jClasses = loadClass(className);
        const unprocessedClasses = jClasses.filter(c => !hookedTargetClasses.includes(c));
        send({"type": "new_class", "class_name":className})
        //console.log("unprocessed class found: " + className + " " + unprocessedClasses.length + "/" + jClasses.length);
        hookedTargetCLassNames.push(className);
        unprocessedClasses.forEach(c => {
            hookAllDeclaredMethodForClass(c);
        });
        hookedTargetClasses.push(...unprocessedClasses);
    }

}


function hookReflection() {
    var internalClasses = ["android.", "org."];
    var classDef = Java.use('java.lang.Class');
    var classLoaderDef = Java.use('java.lang.ClassLoader');
    var loadClassOverloads = classLoaderDef.loadClass.overloads;

    var baseDexClassLoaderDef = Java.use('dalvik.system.BaseDexClassLoader');

    var forName = classDef.forName.overload('java.lang.String', 'boolean', 'java.lang.ClassLoader');
    var reflect = Java.use('java.lang.reflect.Method')
    var member = Java.use('java.lang.reflect.Member')
    var dalvik = Java.use("dalvik.system.DexFile")
    var dalvik2 = Java.use("dalvik.system.DexClassLoader")
    var dalvik3 = Java.use("dalvik.system.PathClassLoader")
    //var dalvik4 = Java.use("dalvik.system.InMemoryDexClassLoader")
    var f = Java.use("java.io.File")
    var url = Java.use("java.net.URL")
    var obj = Java.use("java.lang.Object")
    var fo = Java.use("java.io.FileOutputStream")
    var ThreadDef = Java.use('java.lang.Thread');
    var ThreadObj = ThreadDef.$new();

    // hook constructor of baseDexClassLoader
    var dalviks = [dalvik2, dalvik3]
    dalviks.forEach(dalvik => {
        dalvik.$init.overloads.forEach(function (overload) {
            overload.implementation = function () {
                try{
                    // get file buffer
                    //read file from path
                    console.log('hooking DexClassLoader constructor');
                    var filePath = arguments[0];
    
                    
                    if(filePath  !=null){
                        console.log('file path: ' + filePath);
                        var fileContent = fs.readFileSync(filePath)
                        var message = {}
                        message.type = 'payload_file_loader'
                        message.file_type = 'unknown'
                        message.file_name = filePath
                        send(message, fileContent)
                    }
                   
                }catch(e){
                    console.log(e);
                }
                return this.$init.apply(this, arguments);
    
            }
    
        });
    });

    

    

    // dalvik.loadDex.overloads.forEach(function (overload) {
    //     overload.implementation = function () {
    //         console.log('hooking DexFile loadDex');
    //         return this.loadDex.apply(this, arguments);

    //     };
    // });




    loadClassOverloads.forEach(loadClass => {
        loadClass.implementation = function () {
            const className = arguments[0];
            const res = this.loadClass.apply(this, arguments);
            // hookSpecificClassName(className);
            if (!isSystemClass(className)) {
                searchAndHookNewClassMethod(className);
            }
            return res;
        }
    });

}

Java.performNow(function () {
    hookReflection();
    // bypass_frida_detection_for_jiagu();
});


function stackTrace(log) {
    var traceClasses = [];
    if(log) console.log("--------------------------START STACK-------------------------------------")
    var ThreadDef = Java.use('java.lang.Thread');
    var ThreadObj = ThreadDef.$new();
    var stack = ThreadObj.currentThread().getStackTrace();
    for (var i = 0; i < stack.length; i++) {
        if(log) console.log(i + " => " + stack[i].toString());
        traceClasses.push(stack[i].getClassName());
    }
    if(log) console.log("---------------------------END STACK--------------------------------------");
    return traceClasses;
}

function getMethodSignature(declaringClass, returnType, methodName, argTypes) {
    var argTypeStr = "";
    for(var i = 0; i < argTypes.length; i ++){
        var argClz = convertSmailType(argTypes[i].className);
        argTypeStr += argClz;
        if(i < argTypes.lenght - 1){
            argTypeStr += ",";
        }
    }
    var returnTypeStr = convertSmailType(returnType.className); 
    return "<" + declaringClass + ": " + returnTypeStr + " " + methodName + "(" + argTypeStr + ")>";
}

function convertSmailType(className){
    var suffix = "";

    if(className.startsWith('[')){
        var i = 0;
        while(i < className.length && className.charAt(i) == '[') i++;
        for(var j = 0; j < i; j ++){
            suffix += "[]"
        }
        className = className.substring(i);
    }
    var mapping = {'Z': 'boolean', 
                    'B': 'byte',
                    'S': 'short', 
                    'C': 'char', 
                    'I': 'int',
                    'J': 'long', 
                    'F': 'float', 
                    'D': 'double'}
    if(className in mapping){
        className = mapping[className];
    } else {
        if(className.startsWith('L')){
            className = className.substring(1);
        }
        if(className.endsWith(';')){
            className = className.substring(0, className.length - 1);
        }
    }
    return className + suffix;
}



function bypass_frida_detection_for_jiagu() {
    function hook_pthread_create() {
        var pt_create_func = Module.findExportByName(null, 'pthread_create');
        var detect_frida_loop_addr = null;
        console.log('pt_create_func:', pt_create_func);

        Interceptor.attach(pt_create_func, {
            onEnter: function () {
                if (detect_frida_loop_addr == null) {
                    var base_addr = Module.getBaseAddress('libnative-lib.so');
                    if (base_addr != null) {
                        detect_frida_loop_addr = base_addr.add(0x0000000000000F74)
                        console.log('this.context.x2: ', detect_frida_loop_addr, this.context.x2);
                        if (this.context.x2.compare(detect_frida_loop_addr) == 0) {
                            hook_anti_frida_replace(this.context.x2);
                        }
                    }
                }
            },
            onLeave: function (retval) {
            }
        })
    }

    function hook_anti_frida_replace(addr) {
        console.log('replace anti_addr :', addr);
        Interceptor.replace(addr, new NativeCallback(function (a1) {
            console.log('replace success');
            return;
        }, 'pointer', []));

    }

    setImmediate(hook_pthread_create());
}
