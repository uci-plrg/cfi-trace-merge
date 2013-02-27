#!/bin/bash

for run in $(ls $1); do
	filesInRun=$(ls $1/$run)
	count=0
	for file in $filesInRun; do
		count=$((count+1))
	done
	if [ $count -gt $2 ]; then
		echo $run
	fi
done
