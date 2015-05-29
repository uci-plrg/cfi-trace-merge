#!/bin/bash


HOME_DIR=~/cs-analysis-utils/cs-analysis-utils
CLASS_PATH=$HOME_DIR/classes:$HOME_DIR/lib/java-getopt-1.0.14.jar
MAIN_CLASS=analysis.graph.ClusteringAnalysisGraph
export DYNAMORIO_HOME=~/crowd-safe-dynamorio/crowd-safe-dynamorio/build
export CROWD_SAFE_HASHLOG_DIR=$TMP_LOG

function same-block {
	java -cp $CLASS_PATH $MAIN_CLASS -m $1

}

# import the checking function
. $HOME_DIR/scripts/check-same-binary.sh

# calling checking function from 'check-same-binary.sh'
#check-same-binary $1

