# Docker node cpu simulation
This tool allows you to simulate a node's cpu capabilities on a different machine running Docker. The tool determines the value for docker's `--cpus` configuration that provides the closest performance to the original node. The tuning process is based on the whetstone and dhrystone benchmarks. This can be useful, e.g., for simulating edge nodes on a server.

## Getting a reference benchmark reading
To get the dhrystone and whetstone benchmark values for the node you want to simulate, you must run the benchmarks on the target node. Transfer the code to the target machine and run the benchmarks as follows:

```
cd docker
./run-benchmarks.sh > target-benchmark-values.txt
```

Save the `target-benchmark-values.txt` file. 

> [!NOTE]
> Don't forget that the benchmarks must be run on the machine that you want to simulate.

## Tuning the --cpus configuration
> [!TIP]
> You will need the `target-benchmark-values.txt` file from the previous step.

> [!WARNING]
> The machine where you will be running the simulation must have Docker pre-installed.

To tune the `--cpus` configuration follow these steps:

```
cd docker
./build.sh
cd ..
make
./optimize-docker-cpu target-benchmark-values.txt
```

The tool will run up to 10 iterations. This value is configurable through the `src/optimize-docker-cpu.c` macros. After running the tuning process, select the cpu value that provided the lowest `Avg delta`.

> [!NOTE]
> The tuning procedure must take place on the machine where you will be running your edge databases. If you will be using multiple machines with different hardware capabilities, you must run the --cpus configuration for each of the existing hardware configurations.