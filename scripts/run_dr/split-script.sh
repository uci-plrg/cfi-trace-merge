#!/bin/bash

# Option parsing
while getopts "s:" option
	do
		case $option in
			s ) script_dir=$OPTARG;;
		esac
	done

servers=$(cat ../servers.txt)

# Post option parsing
if [ -z script_dir ] then
	$DEFAULT_SCRIPT_DIR="/scratch/crowd-safe-test-data/input/scripts/scripts"
	echo "Using the default script directory."
	script_dir=$DEFAULT_SCRIPT_DIR
fi

# Split all scripts
