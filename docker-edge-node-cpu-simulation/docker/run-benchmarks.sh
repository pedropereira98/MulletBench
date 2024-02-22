#!/bin/bash

ITERATIONS=5
cd dhrystone-modified-meruje
make dhrystone
make dhrystoneO
echo "Running dhrystone default"

for I in $(eval echo "{1..$ITERATIONS}")
do
    ./dhrystone
done

echo "Running dhrystone with -O"
for I in $(eval echo "{1..$ITERATIONS}")
do
    ./dhrystoneO
done

cd ..
cd whetstone
make

echo "Running whetstone default"
./whetstone 1000000

echo "Running whetstone with -O"
./whetstoneO 1000000

