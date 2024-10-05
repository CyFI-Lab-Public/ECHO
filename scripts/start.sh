#!/bin/bash

# Get the current script's file path
SCRIPT_PATH=$(realpath "$0")

# Get the parent directory of the script
PARENT_DIR=$(dirname "$(dirname "$SCRIPT_PATH")")

if [ ! -d "$PARENT_DIR/output" ]; then
    mkdir "$PARENT_DIR/output"
fi


cd $PARENT_DIR 

# bash scripts/run_data_flow_analysis_module.sh
bash scripts/run_graph_building_module.sh
bash scripts/run_invivo_influence_module.sh
bash scripts/run_remediation_template_generation_module.sh


# output a finish message
echo "==========================="
echo "Finished running all modules"

