# ECHO's Dyanmic Sandboxing Module 

This module provides ECHO's core functionality of automatically simulating an Android malware sample in the sandbox. 
The module contains two parts: 1) an on-device Execution Manager (or agent) that actively traverse the app and triggers events. 2) an Exposed plugin that hooks specific remtoe payload deployment-related APIs and logs necessary context information. 


## Setup 
### Hardware Requirements

Google Pixel 3 phones (with OEM unlock). The phones run a patch version of Android 9 blueline image with version image-blueline-pq1a.181105.017.a1. The original image can be obtained [here](https://developers.google.com/android/images#blueline). Patch and flash the image when installing Magisk.



### Software Requirements
1. [Xposed](https://api.xposed.info/reference/packages.html) is required to inject analysis and api logging code into mawlare's process. We tested and recommand using [EdXposed](https://github.com/ElderDrivers/EdXposed). Details about framework installation can be found [here](https://github.com/ElderDrivers/EdXposed).

### Build From Source 
For building the artifact from the source code, we recommand using Android Studio. We provided a pre-build artifact as ```artifacts/AutoLauncher2.apk```. Please refer to [EdXposed's documentation](https://github.com/ElderDrivers/EdXposed) for configuring and enabling the plugin. 


## Usage

1. Install target malware sample on the phone.

```
adb install -t -g <path_to_malware>
```

2. Send start command to the device.

```
adb shell am broadcast -a externalCmd --es command start_experiment --es pkgName <malware_sample_pacakge_name> --es sampleHash <sample_hash> --ei mode 1
```

3. Collect Result. After the dynamic analysis finishes, the results is automatically saved to ```/sdcard/``` folder in the device. 
Run: 

```
adb pull /sdcard/<malware_samplepackage_name>_dcl_result.json <OUTPUT_FILE_PATH>
```
to get the ```JSON ``` result. 