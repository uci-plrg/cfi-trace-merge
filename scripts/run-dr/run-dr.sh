#!/bin/bash




for ARG in  $*; do
	command $ARG &
	NPROC=$(($NPROC+1))
	if [ "$NPROC" -ge 4 ]; then
		wait
		NPROC=0
	fi
done
