#!/bin/bash

ITERATIONS=5
TEST_SIZE=1G
SUM_THROUGHPUT=0
SUM_IOPS=0

read line #Skip header line
while read line; do
    name=$(echo $line | cut -d ' ' -f 1)
    block=$(echo $line | cut -d ' ' -f 2)
    iodepth=$(echo $line | cut -d ' ' -f 3)
    direct=$(echo $line | cut -d ' ' -f 4)
    workload=$(echo $line | cut -d ' ' -f 5)
    for I in $(eval echo "{1..$ITERATIONS}")
    do
        run=$(fio --minimal --name=test --directory=. --numjobs=1 --size=$TEST_SIZE --time_based --runtime=22s --ramp_time=2s  --direct=$direct --verify=0 --bs=$block --iodepth=$iodepth --rw=$workload --group_reporting=1)
        run=$(echo "$run" | awk '$1 ~ /^[1-9]/')
        #echo "$run"
        throughput=$(echo "$run" | cut -d ';' -f 48)
        iops=$(echo "$run" | cut -d ';' -f 49)
        SUM_THROUGHPUT=$(echo "$SUM_THROUGHPUT + $throughput" | bc)
        SUM_IOPS=$(echo "$SUM_IOPS + $iops" | bc)
    done
    result_throughput=$(echo "scale=4; $SUM_THROUGHPUT / $ITERATIONS" | bc)
    result_iops=$(echo "scale=4; $SUM_IOPS / $ITERATIONS" | bc)
    echo "Test $name with $ITERATIONS iterations. Avg IOPS: $result_iops; Avg Throughput: $result_throughput"
    SUM_THROUGHPUT=0
    SUM_IOPS=0
done

rm test.0.0