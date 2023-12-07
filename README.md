# MulletBench: Multi-layer Edge Time Series Database Benchmark

MulletBench is a TSDB benchmarking tool with a focus on edge-cloud hybrid deployments, providing per-node and per-layer metrics, automatic database cluster deployment and distributed workload generation.

## How to use MulletBench

1. Install initial dependencies:

    ``` bash
    curl https://bootstrap.pypa.io/get-pip.py -o get-pip.py
    python3 get-pip.py --user
    python3 -m pip install --user ansible
    ```

2. Clone the repository:

    ``` bash
    git clone https://github.com/pedropereira98/MulletBench
    ```

The machine where the test is run needs passwordless SSH access to all the machines in the present in the test cluster.

## Running a test

To run a single test run without any automatic graph generation, run:

``` bash
cd src/ansible
ansible-playbook playbook.yaml -i <path to test configuration> 
```

To perform multiple runs with automatic graph generation and cleanup between runs, run:

``` bash
cd src/test-scripts
./run-test.sh -t <path to test configuration>

```

## Configuration options

You can check out all the available configuration options in [options.md](OPTIONS.md).