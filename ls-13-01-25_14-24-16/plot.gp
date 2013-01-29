set multiplot title "Totla hashes and unique hashes for each execution(Ls)"\
layout 1,2 rowsfirst

set size 0.5,0.9
#set size 0.5,0.8
#set origin 0,0

set xrange[0:1100]
set yrange[1:10000]

#set title "Totla hashes and unique hashes for each execution(Tar)"
#set xlabel "Execution"
#set ylabel "Number of Hashes(Latex)"
set logscale y
set key at 1000,2000
#set size 100,100
plot  "unique_hash.dat" using 1:($2 + 1) title 'new-pairs' with lines, "" using 1:5 title 'total-pairs' with lines, "" using 1:6 title 'total-blocks' with lines

#unset ytics
#set ytics at right
unset xlabel
unset ylabel
unset title
set key at 1100,1000

#set size 0.5,0.8
#set origin 0.5,0
set size 0.5,0.9
plot  "unique_hash.dat" using 1:($3 + 1) title 'new-blocks' with lines, "" using 1:4 title 'hash-per-run' with l

unset multiplot
