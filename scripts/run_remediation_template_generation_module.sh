#!/bin/bash

# Function to display usage instructions
usage() {
    echo "Usage: $0 [-g GRAPH_FILE_PATH] [-p PACKAGE_NAME] [-o OUTPUT_PATH]"
    echo
    echo "Arguments:"
    echo "  -g  Path to the graph file with influence (default: output/modeling_graph_with_influence.json)"
    echo "  -p  Package name for the malware (default: com.youku.phone)"
    echo "  -o  Path to the output directory for the template (default: output/template_output)"
    exit 1
}

# Default values for arguments
GRAPH_FILE_PATH="output/modeling_graph_with_influence.json"
PACKAGE_NAME="com.youku.phone"
OUTPUT_PATH="output/template_output"

# Parse command-line arguments
while getopts "g:p:o:" opt; do
    case $opt in
        g) GRAPH_FILE_PATH="$OPTARG" ;;
        p) PACKAGE_NAME="$OPTARG" ;;
        o) OUTPUT_PATH="$OPTARG" ;;
        *) usage ;;
    esac
done

# Get the current script's file path and parent directory
SCRIPT_PATH=$(realpath "$0")
PARENT_DIR=$(dirname "$(dirname "$SCRIPT_PATH")")

# Define the path to the Python script
PYTHON_SCRIPT="$PARENT_DIR/src/RemediationTemplateGenerationModule/index.py"

# Run the Python script with the provided parameters
python3 "$PYTHON_SCRIPT" \
    --graph_file_path "$GRAPH_FILE_PATH" \
    --package_name "$PACKAGE_NAME" \
    --output_path "$OUTPUT_PATH"
