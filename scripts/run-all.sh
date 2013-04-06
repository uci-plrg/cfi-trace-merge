#!/bin/bash

SCRIPT_HOME=/scratch/crowd-safe-test-data/input/scripts/launch-under-cs
export CROWD_SAFE_HASHLOG_DIR=/scratch/crowd-safe-test-data/hashlog/graph
export DYNAMORIO_HOME=~/crowd-safe-dynamorio/crowd-safe-dynamorio/build

progs=("dot" "tar" "cvs" "dd" "sox" "ls") # leave out 'latex'
#progs=("latex" "dot" "tar" "cvs" "dd" "sox" "ls") # leave out 'latex'

for ((i = 0; i < ${#progs[@]}; i++))
do
	prog=${progs[$i]}
	echo "$prog""-under-cs is running now..."
	export CROWD_SAFE_HASHLOG_DIR='/scratch/crowd-safe-test-data/hashlog/graph'
	source $SCRIPT_HOME/"$prog""-under-cs"
	echo "$prog""-under-cs is done."
done

