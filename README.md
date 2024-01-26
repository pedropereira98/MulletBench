# MulletBench: Multi-layer Edge Time Series Database Benchmark

MulletBench is a TSDB benchmarking tool with a focus on edge-cloud hybrid deployments, providing per-node and per-layer metrics, automatic database cluster deployment and distributed workload generation.

## How to use MulletBench

1. Install initial dependencies:

    ``` bash
    curl https://bootstrap.pypa.io/get-pip.py -o get-pip.py
    python3 get-pip.py --user
    python3 -m pip install --user ansible
    ```

    If you get a warning about PATH, you may need to add the mentioned directory to the PATH variable

2. Clone the repository:

    ``` bash
    git clone https://github.com/pedropereira98/MulletBench
    ```

    The machine where the test is run needs passwordless SSH access to all the machines in the present in the test cluster.

3. Install Ansible dependencies

    ``` bash
    cd ansible
    ansible-galaxy install -r requirements.yml
    ```

## Running a test

To run any test, you first need to get a dataset to be used and place it in the directory `ansible/roles/benchmark-client/files/data`.
The current implementation supports the usage of the Passive Vehicular Sensors dataset.

To run a single test run without any automatic graph generation, run:

``` bash
cd ansible
ansible-playbook playbook.yaml -i <path to test configuration> 
```

To perform multiple runs with automatic graph generation and cleanup between runs, run:

``` bash
cd test-scripts
./run-test.sh -t <path to test configuration>

```

## Configuration options

You can check out all the available configuration options in [options.md](OPTIONS.md).