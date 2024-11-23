#!/bin/bash

# Function to display usage instructions
usage() {
    echo "Usage: $0 [-a APK_FILE_PATH] [-j JAR_FILE_PATH] [-p PLATFORM_DIR] [-s SOURCE_SINK_FILE] [-o OUTPUT_DIR]"
    echo
    echo "Arguments:"
    echo "  -a  Path to the malware binary APK file (default: artifacts/com.youku.phone_malware.apk)"
    echo "  -j  Path to the FlowDroid jar file (default: artifacts/soot-infoflow-cmd.jar)"
    echo "  -p  Directory for the Android platform files (default: artifacts/AndroidPlatforms/flplatform1)"
    echo "  -s  Path to the In-Vivo Source Sink file (default: artifacts/SourceSinkFiles/InvivoInfluenceSourceSink.txt)"
    echo "  -o  Directory for output files (default: output)"
    exit 1
}

# Default values for arguments
APK_FILE_PATH="artifacts/com.youku.phone_malware.apk"
JAR_FILE_PATH="artifacts/soot-infoflow-cmd.jar"
PLATFORM_DIR="artifacts/AndroidPlatforms/flplatform1"
SOURCE_SINK_FILE="artifacts/SourceSinkFiles/InvivoInfluenceSourceSink.txt"
OUTPUT_DIR="output"

# Parse command-line arguments
while getopts "a:j:p:s:o:" opt; do
    case $opt in
        a) APK_FILE_PATH="$OPTARG" ;;
        j) JAR_FILE_PATH="$OPTARG" ;;
        p) PLATFORM_DIR="$OPTARG" ;;
        s) SOURCE_SINK_FILE="$OPTARG" ;;
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
FLOWDROID_OUTPUT_PATH="$OUTPUT_DIR/fl_influence_output.json"
JSI_OF_INTEREST_PATH="$OUTPUT_DIR/jsi_of_interest.json"
GRAPH_FILE_PATH="$OUTPUT_DIR/modeling_graph.json"
OUTPUT_PATH="$OUTPUT_DIR/modeling_graph_with_influence.json"

# Run JSI influence analysis with FlowDroid data flow infrastructure
java -jar "$JAR_FILE_PATH" \
    -apkfile "$APK_FILE_PATH" \
    -platformsdir "$PLATFORM_DIR" \
    -sourcessinksfile "$SOURCE_SINK_FILE" \
    --mergedexfiles --onecomponentatatime -js -ot -logcall \
    --outputformat json --outputfile "$FLOWDROID_OUTPUT_PATH" \
    --jsiinterfaceofinterest "$JSI_OF_INTEREST_PATH"

# Define the path to the Python script
PYTHON_SCRIPT="$PARENT_DIR/src/InvivoInfluenceAnalysisModule/index.py"

# Run the Python script with the provided parameters
python3 "$PYTHON_SCRIPT" \
    --graph_file_path "$GRAPH_FILE_PATH" \
    --influence_path "$FLOWDROID_OUTPUT_PATH" \
    --output "$OUTPUT_PATH"
