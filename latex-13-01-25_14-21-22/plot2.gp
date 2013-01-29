#set title "Totla hashes and unique hashes for each execution(Latex)"
#set xlabel "Execution"
#unset xtics
#unset xlabel
#unset ylabel
#unset title
set logscale y
set key at 1100,1800
#set xr [0:]
#set yr [0:]
plot  "unique_hash.dat" using 1:3 title 'new-blocks' with lines, "" using 1:4 title 'hash-per-run' with l
