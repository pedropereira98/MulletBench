# Docker node cpu simulation
This tool uses fio to determine a node's disk capabilities. The values retrieved can be used to adjust the `--device-read-iops` `--device-write-iops` `--device-read-bps` `--device-write-bps`, allowing you to replicate the capabilities of that disk using docker. This can be useful, for example, for simulating an edge node's disk capabilities in Docker.


## Configuration file

The tests are specified using a configuration file. For each test, you must specify: a name, block size, iodepth, O_DIRECT, and workload (as per fio options). 

Below is an example file. Your file should contain the same header:

```
name block_size iodepth direct workload
write_32k_20_direct 32k 20 1 write
write_32k_20 32k 20 0 write
write_32k_40_direct 32k 40 1 write
write_32k_40 32k 40 0 write
write_1024k_20_direct 1024k 20 1 write
write_1024k_20 1024k 20 0 write
read_1024k_20 1024k 20 0 read
```

## Run test

To run a test, simply pass the configuration file to the standard input of the tool as follows:

```
./run-benchmarks.sh < test-list.txt
```

## Run test on docker
We also provide the option to run the tests within a Docker container. In this case, the configuration file must be called `test-list.txt`.

First, build the image.
```
./build-docker.sh
```

Then run it like below:
```
docker run edge-node-disk-bench 
```

