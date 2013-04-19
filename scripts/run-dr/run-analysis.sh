#!/bin/bash

ANALYSIS_HOME=~/cs-analysis-utils/cs-analysis-utils
HASHLOG_DIR=/home/peizhaoo/hashlog
progs=("ls" "tar" "latex" "dot" "dd")
_CLASSPATH=$ANALYSIS_HOME/classes
RESULT_DIR=$ANALYSIS_HOME/results

for prog in ${progs[@]} ;do
	runDir=$HASHLOG_DIR/$prog
	echo $runDir
	java -cp $_CLASSPATH analysis.graph.GraphAnalyzer $runDir > $RESULT_DIR/$prog_result.txt
done
