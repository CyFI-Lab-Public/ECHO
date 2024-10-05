#!/bin/bash

# Get the current script's file path
SCRIPT_PATH=$(realpath "$0")

# Get the parent directory of the script
PARENT_DIR=$(dirname "$(dirname "$SCRIPT_PATH")")

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




# Define the paths to the Python script and the parameters
PYTHON_SCRIPT="$PARENT_DIR/src/GraphBuildingModule/index.py"

DATAFLOW_RESULT_PATHS="output/fl_fetching_output.json,output/fl_loading_output.json"
DYNAMIC_VERTICES_PATHS="artifacts/dynamic_sandboxing_result.json"
OUTPUT_PATH="output/modeling_graph.json"
OUTPUT_JSI_OF_INTEREST_PATH="output/jsi_of_interest.json"

# Run the Python script with the provided parameters
python "$PYTHON_SCRIPT" --df_paths "$DATAFLOW_RESULT_PATHS" --dv_paths "$DYNAMIC_VERTICES_PATHS" --output_path "$OUTPUT_PATH" --output_jsi_of_interest_path "$OUTPUT_JSI_OF_INTEREST_PATH"