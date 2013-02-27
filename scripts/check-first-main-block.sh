#!/bin/bash

# import the checking function
. check-same-binary.sh

PROG_DIRs=/bin

HOME_DIR=~/cs-analysis-utils/cs-analysis-utils
CLASS_PATH=$HOME_DIR/classes:$HOME_DIR/lib/java-getopt-1.0.14.jar
MAIN_CLASS=analysis.graph.ClusteringAnalysisGraph
DST_FILE=$HOME_DIR/dst.txt

TMP_LOG=~/cs-analysis-utils/cs-analysis-utils/log
export DYNAMORIO_HOME=~/crowd-safe-dynamorio/crowd-safe-dynamorio/build
export CROWD_SAFE_HASHLOG_DIR=$TMP_LOG

rm -rf $TMP_LOG/*
echo "" > $DST_FILE

OIFS=$IFS
IFS=":"
read -a dirs <<< $PROG_DIRs
IFS=$OIFS

for dir in ${dirs[@]}; do
	
	for prog in $(ls $dir); do
		# first run the program under DR and store the files in $TMP_LOG
		absolutePath=$(readlink -f $PROG_DIRS/$dir/$prog)
		if [ -f $absolutePath ]; then
			$DYNAMORIO_HOME/runcs -b4 -g $absolutePath --version
		fi
		prog=$absolutePath":"$(java -cp $CLASS_PATH $MAIN_CLASS -t $TMP_LOG/*.bb-graph.*.dat -l $TMP_LOG/*.bb-graph-hash.*.dat)
		echo $prog >> $DST_FILE
		rm -rf $TMP_LOG/*
	done
done

java -cp $CLASS_PATH $MAIN_CLASS -m $DST_FILE

# calling checking function from 'check-same-binary.sh'
