#!/bin/bash

# Get the current script's file path
SCRIPT_PATH=$(realpath "$0")

# Get the parent directory of the script
PARENT_DIR=$(dirname "$(dirname "$SCRIPT_PATH")")

APK_FILE_PATH="artifacts/com.youku.phone_malware.apk"
JAR_FILE_PATH="artifacts/soot-infoflow-cmd.jar"
PLATFORM_DIR="artifacts/AndroidPlatforms/flplatform1"
SOURCE_SINK_FILE="artifacts/SourceSinkFiles/InvivoInfluenceSourceSink.txt"
FLOWDROID_OUTPUT_PATH="output/fl_influence_output.json"
JSI_OF_INTEREST_PATH="output/jsi_of_interest.json"


# Run JSI influence analysis with FlowDroid data flow infrastructure
java -jar "$JAR_FILE_PATH" -apkfile "$APK_FILE_PATH" -platformsdir  "$PLATFORM_DIR" -sourcessinksfile "$SOURCE_SINK_FILE" --mergedexfiles --onecomponentatatime -js -ot -logcall --outputformat json --outputfile "$FLOWDROID_OUTPUT_PATH" --jsiinterfaceofinterest "$JSI_OF_INTEREST_PATH"


# Define the paths to the Python script and the parameters
PYTHON_SCRIPT="$PARENT_DIR/src/InvivoInfluenceAnalysisModule/index.py"

GRAPH_FILE_PATH="output/modeling_graph.json"
INFLUENCE_PATH="output/fl_influence_output.json"
OUTPUT_PATH="output/modeling_graph_with_influence.json"

# Run the Python script with the provided parameters
python "$PYTHON_SCRIPT" --graph_file_path "$GRAPH_FILE_PATH" --influence_path "$INFLUENCE_PATH" --output "$OUTPUT_PATH"