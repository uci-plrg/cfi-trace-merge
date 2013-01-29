#set title "Totla hashes and unique hashes for each execution(Latex)"
#set xlabel "Execution"
set ylabel "Number of Hashes"
set logscale y
set key at 1100,10000
#set xr [0:]
#set yr [0:]
plot  "unique_hash.dat" using 1:2 title 'new-pairs' with lines, "" using 1:5 title 'total-pairs' with lines, "" using 1:6 title 'total-blocks' with lines
