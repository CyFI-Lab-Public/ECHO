#!/bin/bash

# Function to display usage instructions
usage() {
    echo "Usage: $0 [-a APK_FILE_PATH] [-j JAR_FILE_PATH] [-f FETCHING_PLATFORM_DIR] [-l LOADING_PLATFORM_DIR] [-s FETCHING_SOURCE_SINK_FILE] [-t LOADING_SOURCE_SINK_FILE] [-d DYNAMIC_VERTICES_PATHS] [-o OUTPUT_DIR]"
    echo
    echo "Arguments:"
    echo "  -a  Path to the malware binary APK file (default: artifacts/com.youku.phone_malware.apk)"
    echo "  -j  Path to the FlowDroid jar file (default: artifacts/soot-infoflow-cmd.jar)"
    echo "  -f  Directory for the Payload Fetching Platform (default: artifacts/AndroidPlatforms/flplatform1)"
    echo "  -l  Directory for the Payload Loading Platform (default: artifacts/AndroidPlatforms/flplatform2)"
    echo "  -s  Path to the Fetching Source Sink file (default: artifacts/SourceSinkFiles/FetchingSourceSinkDCLFile.txt)"
    echo "  -t  Path to the Loading Source Sink file (default: artifacts/SourceSinkFiles/LoadingSourceSinkDCLFile.txt)"
    echo "  -d  Path to the dynamically generated vertices file (default: artifacts/dynamic_sandboxing_result.json)"
    echo "  -o  Directory for output files (default: output)"
    exit 1
}

# Default values for arguments
APK_FILE_PATH="artifacts/com.youku.phone_malware.apk"
JAR_FILE_PATH="artifacts/soot-infoflow-cmd.jar"
PAYLOAD_FETCHING_PLATFORM_DIR="artifacts/AndroidPlatforms/flplatform1"
PAYLOAD_LOADING_PLATFORM_DIR="artifacts/AndroidPlatforms/flplatform2"
FETCHING_SOURCE_SINK_FILE="artifacts/SourceSinkFiles/FetchingSourceSinkDCLFile.txt"
LOADING_SOURCE_SINK_FILE="artifacts/SourceSinkFiles/LoadingSourceSinkDCLFile.txt"
DYNAMIC_VERTICES_PATHS="artifacts/dynamic_sandboxing_result.json"
OUTPUT_DIR="output"

# Parse command-line arguments
while getopts "a:j:f:l:s:t:d:o:" opt; do
    case $opt in
        a) APK_FILE_PATH="$OPTARG" ;;
        j) JAR_FILE_PATH="$OPTARG" ;;
        f) PAYLOAD_FETCHING_PLATFORM_DIR="$OPTARG" ;;
        l) PAYLOAD_LOADING_PLATFORM_DIR="$OPTARG" ;;
        s) FETCHING_SOURCE_SINK_FILE="$OPTARG" ;;
        t) LOADING_SOURCE_SINK_FILE="$OPTARG" ;;
        d) DYNAMIC_VERTICES_PATHS="$OPTARG" ;;
        o) OUTPUT_DIR="$OPTARG" ;;
        *) usage ;;
    esac
done

# Get the current script's file path and parent directory
SCRIPT_PATH=$(realpath "$0")
PARENT_DIR=$(dirname "$(dirname "$SCRIPT_PATH")")

# Ensure output directory exists
if [ ! -d "$PARENT_DIR/$OUTPUT_DIR" ]; then
    mkdir -p "$PARENT_DIR/$OUTPUT_DIR"
fi

# Set output file paths
FETCHING_FLOWDROID_OUTPUT_PATH="$OUTPUT_DIR/fl_fetching_output.json"
LOADING_FLOWDROID_OUTPUT_PATH="$OUTPUT_DIR/fl_loading_output.json"

# Run Payload Fetching and Loading data flow analysis with FlowDroid data flow infrastructure
java -jar "$JAR_FILE_PATH" \
    -apkfile "$APK_FILE_PATH" \
    -platformsdir "$PAYLOAD_FETCHING_PLATFORM_DIR" \
    -sourcessinksfile "$FETCHING_SOURCE_SINK_FILE" \
    --mergedexfiles --outputlinenumbers --onecomponentatatime --dynamicloading --paths \
    --outputformat json --outputfile "$FETCHING_FLOWDROID_OUTPUT_PATH"

java -jar "$JAR_FILE_PATH" \
    -apkfile "$APK_FILE_PATH" \
    -platformsdir "$PAYLOAD_LOADING_PLATFORM_DIR" \
    -sourcessinksfile "$LOADING_SOURCE_SINK_FILE" \
    --mergedexfiles --outputlinenumbers --onecomponentatatime --dynamicloading --paths \
    --outputformat json --outputfile "$LOADING_FLOWDROID_OUTPUT_PATH"

# Define the paths to the Python script and its parameters
PYTHON_SCRIPT="$PARENT_DIR/src/GraphBuildingModule/index.py"
DATAFLOW_RESULT_PATHS="$OUTPUT_DIR/fl_fetching_output.json,$OUTPUT_DIR/fl_loading_output.json"
OUTPUT_PATH="$OUTPUT_DIR/modeling_graph.json"
OUTPUT_JSI_OF_INTEREST_PATH="$OUTPUT_DIR/jsi_of_interest.json"

# Run the Python script with the provided parameters
python3 "$PYTHON_SCRIPT" \
    --df_paths "$DATAFLOW_RESULT_PATHS" \
    --dv_paths "$DYNAMIC_VERTICES_PATHS" \
    --output_path "$OUTPUT_PATH" \
    --output_jsi_of_interest_path "$OUTPUT_JSI_OF_INTEREST_PATH"
