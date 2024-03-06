#!/bin/bash

ITERATIONS=1
TEST_SIZE=1G

read line #Skip header line
while read line; do
    name=$(echo $line | cut -d ' ' -f 1)
    block=$(echo $line | cut -d ' ' -f 2)
    iodepth=$(echo $line | cut -d ' ' -f 3)
    direct=$(echo $line | cut -d ' ' -f 4)
    workload=$(echo $line | cut -d ' ' -f 5)
    for I in $(eval echo "{1..$ITERATIONS}")
    do
        fio --name=write_throughput --directory=. --numjobs=1 --size=$TEST_SIZE --time_based --runtime=22s --ramp_time=2s  --direct=$direct --verify=0 --bs=$block --iodepth=$iodepth --rw=$workload --group_reporting=1
    done
done





