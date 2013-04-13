#!/bin/bash

export DYNAMORIO_HOME=~/crowd-safe-dynamorio/crowd-safe-dynamorio/build
HASHLOG_DIR=/scratch/crowd-safe-test-data/output/hashlog
GLOBAL_HASHLOG_DIR=~/hashlog

RUNCS=runcs
pkill $RUNCS

if [ -z $1 ] ;then
	echo "Didn't specify the generated script directory"
	exit
fi

SERVER_DIR=$1

function get_prog_name {
	echo $1 | cut -d'-' -f1
}


for script in $(ls $SERVER_DIR) ;do
	if [[ "$script" =~ .*under-cs ]] ;then
		prog_name=$(get_prog_name $script)
		echo "$(hostname) is running script $SERVER_DIR/$script"
		export CROWD_SAFE_HASHLOG_DIR=$HASHLOG_DIR
		echo $CROWD_SAFE_HASHLOG_DIR
		source $SERVER_DIR/$script
		mkdir -p $GLOBAL_HASHLOG_DIR/$prog_name
		mv $HASHLOG_DIR/$prog_name*/$prog_name/run* $GLOBAL_HASHLOG_DIR/$prog_name &
		#echo "mv $HASHLOG_DIR/$prog_name*/$prog_name/run* $GLOBAL_HASHLOG_DIR/$prog_name &"
		#rm -rf $HASHLOG_DIR/*
	fi
done
