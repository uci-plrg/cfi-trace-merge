#!/bin/bash


#HOME_DIR=~/cs-analysis-utils/cs-analysis-utils
#SAME_BLOCK_FILE=$HOME_DIR/same-block.txt

function check-same-binary {
	# $1 is the file that contains the programs with the same
	# first main block
	for line in $(cat $1); do
		OIFS=$IFS
		IFS=":"
		read -a progs <<< $line
		IFS=$OIFS
		diff ${progs[0]} ${progs[1]}
	done
}

#check-same-binary $SAME_BLOCK_FILE
