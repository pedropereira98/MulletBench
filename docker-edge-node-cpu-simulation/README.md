# Docker node cpu simulation
This tool allows you to simulate a node's cpu capabilities by determining the value for docker's `--cpus` configuration that provides the closest performance to the original node. The tuning process is based on the whetstone and dhrystone benchmarks.

## Getting a reference benchmark reading
To get the dhrystone and whetstone benchmark values for the edge node you want to simulate, you must run the benchmarks on the target edge node. Transfer the code to the target machine and run the benchmarks as follows:


```
cd docker
./run-benchmarks.sh > target-benchmark-values.txt
```

Save the `target-benchmark-values.txt` file. 

> [!NOTE]
> The benchmark should be run on the machine that you want to simulate.

## Tuning the --cpus configuration

> [!NOTE]
> The tuning procedure must take place on the machine where you will be running your edge databases. If you will be using multiple 