ECHO

# ECHO

This is the codebase for the [NDSS 2025](https://www.ndss-symposium.org/) paper titled "Hitchhiking Vaccine: Enhancing Botnet Remediation With Remote Code Deployment Reuse".
The proposed pipeline, ECHO, expands malware takedown efforts by leveraging the malware's built-in update mechanisms to distribute crafted remediation payloads. This approach enables the covert and timely removal of bots. The pipeline supports the analysis of Android malware and facilitates various actions, ranging from covertly warning users about malware infections to uninstalling the malware.




## Dynamic Sandboxing Module 

**WARNING: The steps below involve installing and executing malware, conducting live communication with malware C&C servers, and modifying and patching the ROM image of physical phones, all of which carry intrinsic risks to the security of your device and the privacy of your information. Collect and run active malware at your own risk.**


This is the first module of ECHO's pipeline that simulates the input malware APK file in an isolated sandboxing environment. The module performs component-level force execution and passively logs API call information.


### Setup 

#### Hardware Requirement 

1. A Google Pixel 3 mobile phone (or similar) running Android AOSP 9.0. The module is tested on the official image [9.0.0 (PQ1A.181105.017.A1, Nov 2018)](https://developers.google.com/android/images#blueline).
2. A Linux/Debian-based device. This is required to run the MitM proxy for C&C redirection.

#### Software Requirement 
1. [Android Studio](https://developer.android.com/studio) is required to build the dynamic sandbox agent app and the method hooking plugin. See the official website for more details.


2. [Xposed](https://api.xposed.info/reference/packages.html) is required to inject analysis code into the malware process. Detailed instructions on configuring the phone and installing Xposed can be found in the [DynamicSandboxingModule](src/DynamicSandboxingModule).

3. [MitMProxy](https://mitmproxy.org/). Generally, users should skip this module since active remotely controlled malware should have live C&C backends. However, the backend of the provided test case [com.youku.phone_malware.apk](artifacts/com.youku.phone_malware.apk), has been neutralized and inactive. To enable users to test ECHO's full pipeline, we provide a MiTM proxy with a customized script to simulate the live C&C server. The code in this folder does not contribute to ECHO's pipeline. For more details, see [RemediationSimulation/DynamicSandboxingMITMProxy](RemediationSimulation/DynamicSandboxingMITMProxy). 



#### Notes for Artifact Evaluation Users 
We understand the limited access reviewers may have to specific hardware devices. It is challenging to run this module on a generic desktop/emulator. Alternatively, we provide pre-collected results for this module in the ```artifacts``` folder as ```dynamic_sandboxing_result.json```. You may also refer to the paper or the [demo video](https://www.youtube.com/channel/UCXWT7OaYugn1vSIqeFoqdsw) for more details.

### Usage 
1. Install the target malware sample on the phone.

```
adb install -t -g <path_to_malware>
```

2. Send the start command to the device.

```
adb shell am broadcast -a externalCmd --es command start_experiment --es pkgName <malware_sample_pacakge_name> --es sampleHash <sample_hash> --ei mode 1
```

3. Collect Result.

```
adb pull /sdcard/<malware_samplepackage_name>_dcl_result.json <OUTPUT_FILE_PATH>
```


### Interpretation of Reports

The ```output.json``` file (or pre-collected ```dynamic_sandboxing_result.json``` file) contains: 

1. A list of dynamically logged APIs with their multi-threading stack trace, capturing cross-thread stack traces when code is executed in a multi-threading process.
For each logged API, the stack trace contains lines like `End of Thread Stack at ...`, which indicates the call stack traves above this line, and the call stack traces below this line are gathered from different threads.  


2. Essential information associated with each API call for further payload deployment routine graph generation. For example, if an API call involves file access (e.g., java.io.FileOutputStream:write), the file path is logged. This enables asynchronous data flow tracking when one process dumps the payload to a local file and another process later loads the file. For example, the value from `filePath` for an API log with `"type" == "fileRead"` indicates the file path that is read by this API call. 

3. Essential information associated with C&C servers (i.e., URL) and in-vivo influence (see details in the paper). By searching API logs with `"type": "network"`, the remote URL is logged if it is valid. 





## Data Flow Analysis Module 

This module captures the data dependency relationships between potential API calls. It is built on top of [FlowDroid](https://github.com/secure-software-engineering/FlowDroid) and analyzes malware samples statically. 

### Setup 

#### Hardware Requirements 

A Linux Machine. The framework was tested on a Ubuntu 20.04.6 LTS machine with a 4-core CPU and 16GB Memory. However, it should work with any recent version of Ubuntu or any Debian 11 and up (64-bit) machines.

#### Software Requirements 
1. Java 1.8 JDK. The framework was tested with OpenJDK version 1.8.0 with Java Runtime Environment (JRE). Install with:
```
sudo apt install openjdk-8-jdk
```

2. (Optional) Android SDKs. No additional setup is required to analyze the malware in Usage, as the necessary Android SDK library reference is included. To analyze additional malware targeting different Android SDK versions, place the corresponding SDK’s android.jar file. This can be installed with Android Studio; see details in Android’s official [SDK Platform release notes](https://developer.android.com/tools/releases/platforms).


#### Build From Source
We provide a pro-compiled ```artifacts/soot-infoflow-cmd.jar``` binary for easy deployment. 
For users who aim to build this module from the source, please refer to [DataFlowModule](https://github.com/CyFI-Lab-Public/ECHO/tree/main/src/DataFlowModule) for more details. 


### Usage 
The usage of this module has been integrated into the Graph Building Module and Invivo Influence Analysis Module. See those for more details.

## Graph Building Module 
This module is used to aggregate results from first two modules and formally model dynamically logged API calls and statically captured data flow information into a graph. This graph will represent the payload deployment routine implemented by analyzed malware. 


### Setup 

### Software Requirement 

1. Python==3.8+. No additional dependency is required. 

### Usage 

Run:
 ```
bash scripts/run_graph_building_module.sh
```

We have provided pre-configured arguments in the script. Use `--help` to view details about the customizable parameters.

This will first run the Data Flow Analysis Module with pre-configured parameters. Next, it will pass pre-configured parameters to ```src/GraphBuildingModule/index.py```.
 See the scripts for more details on customizable parameters.




### Interpretation of Reports
The first two output files (```fl_fetching_output.json, fl_loading_output.json```) log statically captured data dependencies between API calls. Each file contains a list of source-sin pairs. For each source-sink pair, it will have four fields: "source" refers to the source API, "sink" refers to the sink API. Each API object contains the method name and the static call stack trace. "entryPointClass" refers to the Android component being used to generate the CFG. (Refer [FlowDroid](https://github.com/secure-software-engineering/FlowDroid) for more details about this field). "path" refers to a projected path that is statically generated from the stack traces of source and sink APIs. 


The output file ```modeling_graph.json``` presents the payload deployment routines modeling, organized as a graph. It contains a list fo routines modeled from the analyzed malware sample. 

For each routine, the vertices are shown under the "vertices" key, containing method signatures, stack traces, accessed file paths (if applicable), and other necessary information for graph generation.
The "edges" list in the JSON file contains the edges between vertices and how they were generated (e.g., via data flow, shared file access, etc.).
According to the example outputs, which find 1 routine from the malware. 
To the edges, the first edge represents a data dependency between a network request call (`HttpResponse.execute`) to a file write call (`FileOutputStream.write` ), with corresponding information like the remote server's URL and the file path accordingly. 2nd edge shows the file write call is connected to the ZipStream initialization call, modeling the malware's behavior of unzipping the payload. Similarly, the third edge between the `ZipInputStream.<init>` call and the file write call (i.e., `FileOutputStream.write`) represents malware's writing the unzipped file to storage. Combining all the edges, the graph models malware's payload fetching, loading, and executing behaviors.  


## In-vivo Influence Analysis Module

### Setup 

### Software Requirement 

1. Python==3.8+. No additional dependency is required. 

### Usage 

Run: 
 ```
bash scripts/run_invivo_influence_module.sh
```
This will pass pre-configured parameters to ```src/InvivoInfluenceAnalysisModule/index.py```. See the scripts for more details about customizable parameters.

We have provided pre-configured arguments in the script. Use `--help` to view details about the customizable parameters.


### Interpretation of Reports
This module outputs a file named ```output/modeling_graph_with_influence.json```, representing how the payload can affect the malware. 
The results extend the graph-building output, with an added "influences" dictionary for each routine.

For instance, for payload deployment routines running a Java binary, the influence could include JavaScript Interface (JSI) methods that can invoke APIs to disrupt the malware. For example, the ```runCmd``` JSI method can call the ```Runtime.exec``` method, enabling arbitrary Linux shell commands to be executed.


This module outputs a file named ```output/modeling_graph_with_influence.json```.
The results represent how the payload can affect the malware. 
This is the output of running ```run_invivo_influence_module.sh``` script. 
ECHO extends the result from graph building by adding an "influences" dictionary for each routine.
In this file, users will observe a new filed `influences` for each identified payload deployment routine. In the example outputs, ECHO finds a `WebView interface` that enables a Linux Shell code execution capability. Specifically, the malware implements the  `Runtime.exec` that takes arbitrary parameters from the `runCmd` method, which is implemented by malware developers. ECHO finds this API can be invoked from the JavaScript Payload being executed by WebView, which enables attackers the code execution capability. Users are recommended to check the paper for more details. 




## Remediation Template Generation Module

### Setup 

### Software Requirement 

1. Python==3.8+. No additional dependency is required. 

### Usage 

Run
 ```
bash scripts/run_remediation_template_generation_module.sh
```

This will pass a pre-configured parameter to ```src/RemediationTemplateGenerationModule/index.py```. 
See scripts for more details about customizable parameters.  

We have provided pre-configured arguments in the script. Use `--help` to view details about the customizable parameters.



### Interpretation of Reports

This module outputs remediation payload templates in the ```output/template_output``` folder as multiple JS files. For the provided sample, the remediation template will call the previously identified ```runCmd``` API within the JavaScript payload to call the system API ```Runtime.exec```, enabling both victim notification and malware deletion capabilities.

We also provide the demo video to showcase the remediation via deploying this remediation payload to malware samples through a MITM proxy. Besides, users can use additional artifacts in [RemediationSimulation/DynamicSandboxingMITMProxy](RemediationSimulation/DynamicSandboxingMITMProxy) to reproduce this remediation by themselves, see more details in [RemediationSimulation/DynamicSandboxingMITMProxy](RemediationSimulation/DynamicSandboxingMITMProxy). 





## Artifact Evaluation 

### Setup 

For a quick setup and deployment, users can run all modules (except the Dynamic Sandboxing Module) in sequence using:

```
bash scripts/start.sh 
```

Pre-configured input/output parameters are included in each script within the scripts folder. Users can customize these parameters as necessary. The results will be presented in the output folder. Check each module's description for details about interpreting the output. Example outputs are provided in the example_outputs folder for reviewers' reference.

We thank the artifact evaluation committee and reviewers for their efforts.

### Update: 
We updated the code for building the automation pipeline to run ECHO at scale, and the hashes for malware samples used in our evaluation as [artifacts/malware_hashes.txt](artifacts/malware_hashes.txt)


