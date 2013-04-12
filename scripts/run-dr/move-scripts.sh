#!/bin/bash

# Kill all remote ssh process
kill $(ps aux | grep 'ssh.*eecs.uci.edu..*' | awk '{print $2}')

RUNDR_DIR=~/cs-analysis-utils/cs-analysis-utils/scripts/run-dr
SCRIPT_DIR=~/cs-analysis-utils/cs-analysis-utils/scripts/run-dr/generated-scripts
DYNAMORIO_HOME=~/crowd-safe-dynamorio/crowd-safe-dynamorio/build
CROWD_SAFE_HASHLOG_DIR=/scratch/crowd-safe-test-data/output/hashlog
mkdir -p $CROWD_SAFE_HASHLOG_DIR

servers=$(ls $SCRIPT_DIR)
for server in ${servers[@]} ;do
	ssh $server "rm -rf $CROWD_SAFE_HASHLOG_DIR/*;export DYNAMORIO_HOME=$DYNAMORIO_HOME;$RUNDR_DIR/run-dr.sh $SCRIPT_DIR/$server;mv $CROWD_SAFE_HASHLOG_DIR ~/hashlog" &
done
