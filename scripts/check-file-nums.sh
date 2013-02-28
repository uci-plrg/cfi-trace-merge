#!/bin/bash

# check which run has execute multiple programs
# param: $1 is the directory that contains many runs generated
#        by the scripts (format like latex-timestamp-latex/)
#        $2 is the number of files in each run### directory
function check-file-runs {
	for run in $(ls $1); do
		filesInRun=$(ls $1/$run)
		read -a files <<<$filesInRun
		count=${#files[@]}
		if [ $count -gt $2 ]; then
			echo $1/$run
		fi
	done
}

# check all the tested programs, it will call 'check-file-runs'
# param: $1 is the directory that contains all the hashlog (e.g.
#        '/scratch/crowd-safe-test-data/hashlog/graph')
function check-all-file-runs {
	for prog in $(ls $1); do
		dir=$1/$prog/$(ls $1/$prog)
		check-file-runs $dir 15
	done
}
check-all-file-runs $1
