#!/bin/bash

# Get the current script's file path
SCRIPT_PATH=$(realpath "$0")

# Get the parent directory of the script
PARENT_DIR=$(dirname "$(dirname "$SCRIPT_PATH")")

# Define the paths to the Python script and the parameters
PYTHON_SCRIPT="$PARENT_DIR/src/RemediationTemplateGenerationModule/index.py"

GRAPH_FILE_PATH="output/modeling_graph_with_influence.json"
PACKAGE_NAME="com.youku.phone"
OUTPUT_PATH="output/template_output"

# Run the Python script with the provided parameters
python3 "$PYTHON_SCRIPT" --graph_file_path "$GRAPH_FILE_PATH" --package_name "$PACKAGE_NAME" --output_path "$OUTPUT_PATH"