#!/bin/bash

PROG_PATTERN=build/runcs
pkill $PROG_PATTHERN
scripts=$(ls $1)

for script in ${scripts[@]} ;do
	source $1/$script
done
