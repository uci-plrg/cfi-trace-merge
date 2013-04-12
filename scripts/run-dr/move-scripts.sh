#!/bin/bash

SCRIPT_DIR=~/cs-analysis-utils/cs-analysis-utils/scripts/run-dr/generated-scripts
DYNAMORIO_HOME=~/crowd-safe-dynamorio/crowd-safe-dynamorio/build
CROWD_SAFE_HASHLOG_DIR=/scratch/crowd-safe-test-data/output/hashlog
mkdir -p $CROWD_SAFE_HASHLOG_DIR

servers=$(ls $SCRIPT_DIR)
server=dc-1.eecs.uci.edu
for server in ${servers[@]} ;do
	ssh $server "export DYNAMORIO_HOME=$DYNAMORIO_HOME;$SCRIPT_DIR/$server/dd-under-cs" &
done
