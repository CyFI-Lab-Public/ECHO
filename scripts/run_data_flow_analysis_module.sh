#!/bin/bash

# Get the current script's file path
SCRIPT_PATH=$(realpath "$0")

# Get the parent directory of the script
PARENT_DIR=$(dirname "$(dirname "$SCRIPT_PATH")")

#check if output folder exists, if not, create it
if [ ! -d "$PARENT_DIR/output" ]; then
    mkdir "$PARENT_DIR/output"
fi


APK_FILE_PATH="artifacts/com.youku.phone_malware.apk"
JAR_FILE_PATH="artifacts/soot-infoflow-cmd.jar"

PAYLOAD_FETCHING_PLATFORM_DIR="artifacts/AndroidPlatforms/flplatform1"
PAYLOAD_LOADING_PLATFORM_DIR="artifacts/AndroidPlatforms/flplatform2"

FETCHING_SOURCE_SINK_FILE="artifacts/SourceSinkFiles/FetchingSourceSinkDCLFile.txt"
LOADING_SOURCE_SINK_FILE="artifacts/SourceSinkFiles/LoadingSourceSinkDCLFile.txt"

FETCHING_FLOWDROID_OUTPUT_PATH="output/fl_fetching_output.json"
LOADING_FLOWDROID_OUTPUT_PATH="output/fl_loading_output.json"

# Run Payload Fetching and Loading data flow analysis with FlowDroid data flow infrastructure
java -jar "$JAR_FILE_PATH" -apkfile "$APK_FILE_PATH" -platformsdir  "$PAYLOAD_FETCHING_PLATFORM_DIR" -sourcessinksfile "$FETCHING_SOURCE_SINK_FILE" --mergedexfiles --outputlinenumbers --onecomponentatatime --dynamicloading --paths --outputformat json --outputfile "$FETCHING_FLOWDROID_OUTPUT_PATH"


java -jar "$JAR_FILE_PATH" -apkfile "$APK_FILE_PATH" -platformsdir  "$PAYLOAD_LOADING_PLATFORM_DIR" -sourcessinksfile "$LOADING_SOURCE_SINK_FILE" --mergedexfiles --outputlinenumbers --onecomponentatatime --dynamicloading --paths --outputformat json --outputfile "$LOADING_FLOWDROID_OUTPUT_PATH"
