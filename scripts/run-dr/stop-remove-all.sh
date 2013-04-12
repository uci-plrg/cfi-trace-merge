#!/bin/bash

GLOBAL_HASHLOG_DIR=~/hashlog
mkdir -p $GLOBAL_HASHLOG_DIR
rm -rf $GLOBAL_HASHLOG_DIR/*

RUNDR_DIR=~/cs-analysis-utils/cs-analysis-utils/scripts/run-dr
SCRIPT_DIR=~/cs-analysis-utils/cs-analysis-utils/scripts/run-dr/generated-scripts
# Kill all remote ssh process
kill $(ps aux | grep 'ssh.*eecs.uci.edu..*' | awk '{print $2}')
DYNAMORIO_HOME=~/crowd-safe-dynamorio/crowd-safe-dynamorio/build
CROWD_SAFE_HASHLOG_DIR=/scratch/crowd-safe-test-data/output/hashlog
rm -rf $CROWD_SAFE_HASHLOG_DIR/*
RUNCS=runcs
SCRIPT=run-dr.sh

servers=$(ls $SCRIPT_DIR)
for server in ${servers[@]} ;do
	ssh $server "pkill $SCRIPT;pkill $RUNCS;rm -rf $CROWD_SAFE_HASHLOG_DIR/*" &
done

