#!/bin/bash


RUNDR_DIR=~/cs-analysis-utils/cs-analysis-utils/scripts/run-dr
SCRIPT_DIR=~/cs-analysis-utils/cs-analysis-utils/scripts/run-dr/generated-scripts
# Kill all remote ssh process
kill $(ps ax | grep 'ssh.*eecs.uci.edu' | awk '{print $2}')
PROG_PATTERN=crowd-safe-dynamorio/crowd-safe-dynamorio/build/runcs


servers=$(ls $SCRIPT_DIR)
server=dc-1.eecs.uci.edu
for server in ${servers[@]} ;do
	ssh $server "kill $(ps aux | grep "$PROG_PATTERN" | awk '{print $2}')" &

done

