ECHO

# ECHO

This is the codebase for the [NDSS 2025](https://www.ndss-symposium.org/) paper titled "Hitchhiking Vaccine: Enhancing Botnet Remediation With Remote Code Deployment Reuse".
The proposed pipeline, ECHO, expands malware takedown efforts by leveraging the malware's built-in update mechanisms to distribute crafted remediation payloads, enabling covert and timely removal of bots. The pipeline supports the analysis of Android malware and facilitates actions ranging from covertly warning users about malware infection to uninstalling the malware.


## Dynamic Sandboxing Module 

**WARNING: The steps below involve installing and executing malware, conducting live communication with malware C&C servers, and modifying and patching the ROM image of physical phones, all of which carry intrinsic risks to the security of your device and the privacy of your information. Collect and run active malware at your own risk.**


This is the first module of ECHO's pipeline that simulates the input malware APK file in an isolated sandboxing environment. The module performs component-level force execution and passively logs API call information.



### Setup 

#### Hardware Requirement 

1. A Google Pixel 3 mobile phone (or similar) runnign Android AOSP 9.0. The module is tested on official image [9.0.0 (PQ1A.181105.017.A1, Nov 2018)](https://developers.google.com/android/images#blueline).
2. A Linux/Debian-based device. This is required to run the MitM proxy for C&C redirection.

#### Software Requirement 
1. [Android Studio](https://developer.android.com/studio) is required to build the dynamic sandbox agent app and the method hooking plugin. See the official website for more details.


2. [Xposed](https://api.xposed.info/reference/packages.html) s required to inject analysis code into the malware process. Detailed instructions on configuring the phone and installing Xposed can be found in the [DynamicSandboxingModule](src/DynamicSandboxingModule).

3. [MitMProxy](https://mitmproxy.org/) is required to interpret network requests and log corresponding information. See [DynamicSandboxingMITMProxy](src/DynamicSandboxingMITMProxy) for more details. 




#### Notes for Artifact Evaluation Users 
We understand the limited access reviewers may have to specific hardware devices. It is challenging to run this module on a generic desktop/emulator. As an alternative, we provide pre-collected results for this module in the ```artifacts``` folder as ```dynamic_sandboxing_result.json```. You may also refer to the paper or the [demo video](https://www.youtube.com/channel/UCXWT7OaYugn1vSIqeFoqdsw) for more details.

### Usage 
1. Install target malware sample on the phone.

```
adb install -t -g <path_to_malware>
```

2. Send start command to the device.

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

2. Essential information associated with each API call for further payload deployment routine graph generation. For example, if an API call involves file access (e.g., java.io.FileOutputStream:write), the file path is logged. This enables asynchronous data flow tracking when one process dumps the payload to a local file and another process later loads the file.

3. Essential information associated with C&C servers (i.e., URL) and in-vivo influence (see details in the paper).



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

2. (Optional) Android SDKs. For analyzing the malware in Usage, no additional setup is required, as the necessary Android SDK library reference is included. For analyzing additional malware targeting different Android SDK versions, place the corresponding SDK’s android.jar file. This can be installed with Android Studio; see details in Android’s official [SDK Platform release notes](https://developer.android.com/tools/releases/platforms).


#### Build From Source
We provide a pro-compiled ```artifacts/soot-infoflow-cmd.jar``` binary for easy deployment. 
For users aims to build this module from the source, please refer to [DataFlowModule](https://github.com/CyFI-Lab-Public/ECHO/tree/main/src/DataFlowModule) for more details. 


### Usage 
The usage of this module has been integrated in Graph Building Module and Invivo Influence Analysis Module. See those for mroe details.




## Graph Building Module 
This module is used to aggregate resutls from first two modules and formally modeling dynamcally logged API calls and statically captured data flow information into a graph. This graph will represent the payload deployment routine implemented by analyzed malware. 


### Setup 

### Software Requirement 

1. Python==3.8+. No additional dependency is required. 

### Usage 

Run:
 ```
bash scripts/run_graph_building_module.sh
```

This will first run the Data Flow Analysis Module with pre-configured parameters. Next, it will pass pre-configured parameters to ```src/GraphBuildingModule/index.py```.
 See the scripts for more details on customizable parameters.

### Interpretation of Reports
The first two output files (```fl_fetching_output.json, fl_loading_output.json```) log statically captured data dependencies between API calls. For each source-sink pair, a projected path and statically generated stack trace are presented for each source/sink API.

The output file ```graph_modeling.json``` also lists the payload deployment routine modeling, organized as a graph. The vertices are shown under the "vertices" key, containing modeled method signatures, stack traces, accessed file paths (if applicable), and other necessary information for graph generation. 
The "edges" list in the JSON file contains the edges between vertices and how they were generated (e.g., via data flow, shared file access, etc.).



## In-vivo Influence Analysis Module

### Setup 

### Software Requirement 

1. Python==3.8+. No additional dependency is required. 

### Usage 

Run: 
 ```
bash scripts/run_graph_building_module.sh
```
This will pass pre-configured parameters to ```src/InvivoInfluenceAnalysisModule/index.py```. See the scripts for more details about customizable parameters.

### Interpretation of Reports
This module outputs a file named ```output/modeling_graph_with_influence.json```, representing how the payload can affect the malware. 
The results are an extension of the graph building output, with an added "influences" dictionary for each routine.

For instance, for payload deployment routines running a Java binary, the influence could include JavaScript Interface (JSI) methods that can invoke APIs to disrupt the malware. For example, the ```runCmd``` JSI method can call the ```Runtime.exec``` method, enabling arbitrary Linux shell commands to be executed.





This module outputs a file named ```output/modeling_graph_with_influence.json```.
The results represent how the payload can affect the malware. 
This is the output of running ```run_invivo_influence_module.sh``` script. 
ECHO extends the result from graph building by adding an "influences" dictionary for each routine.
The influence for payload deployment routine running a Java binary will be a string indicating arbitrary binary code. 
As shown with provided malware, the influence for payload will list Javascript Interface (JSI) methods that can be invoked from the payload as well as APIs that can disrupt the malware. 
In the example, users will see a ```runCmd``` JSI method can call a ```Runtime.exec``` method, which further enables running arbitrary Linux shell commands. 




## Remediation Template Generation Module

### Setup 

### Software Requirement 

1. Python==3.8+. No additional dependency is required. 

### Usage 

Run
 ```
bash scripts/run_remediation_template_generation_module.sh
```

This will pass pre-configged parameter to ```src/RemediationTemplateGenerationModule/index.py```. 
See scripts for more details about customizable parameters.  


### Interpretation of Reports

This module outputs remediation payload templates in the ```output/template_output``` folder as multiple JS files. For the provided sample, the remediation template will call the previously identified ```runCmd``` API within the JavaScript payload to call the system API ```Runtime.exec```, enabling both victim notification and malware deletion capabilities.

We also provide the demo video to showcase the remediation via deploying this remediation payload to malware samples through a MITM proxy. However, the users can use additional artifacts in [DynamicSandboxingMITMProxy](src/DynamicSandboxingMITMProxy) to reproduce this remediation by themselves, see more details in [DynamicSandboxingMITMProxy](src/DynamicSandboxingMITMProxy). 





## Artifact Evaluation 

### Setup 

For a quick setup and deployment, users can run all modules (except Dynamic Sandboxing Module) in sequence using:

```
bash scripts/start.sh 
```

Pre-configured input/output parameters are included in each script within the scripts folder. Users can customize these parameters as necessary. The results will be presented in the output folder. Check each module's description for details about interpreting the output. Example outputs are provided in the example_outputs folder for reviewers' reference.

We thank the artifact evaluation committee and reviewers for their efforts.





