
from json import load
import sys
import os
import yaml
import re
import subprocess
import pandas as pd
from datetime import datetime
from enum import Enum
import argparse

ANSIBLE_PATH = "~/MulletBench/ansible" # Path to the Ansible playbook directory
OUTPUT_PATH = "outputs/" # Path to the output directory where results will be stored

MAX_RUNS = 10
STOP_THRESHOLD = 0.02
MAX_DECIMAL_PLACES = 5
INITIAL_VALUE = 1
DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"

TIME = "Total time"
INSERT_VOLUME = "Inserted volume"
INSERT_COUNT = "Inserted count"
INSERT_RATE = "Insertion rate"
INSERT_LATENCY = "Average latency"
QUERY_COUNT = "Query count"
QUERY_RATE = "Query rate"
QUERY_LATENCY = "Average"

NUM_REGEX = re.compile(r"\d+(\.\d+)?")
HEADER_REGEX = re.compile(r"[^:]+:\s*\n")

argParser = argparse.ArgumentParser(prog="optimize_docker_cpu.py")
argParser.add_argument("reference_results", help="Path to the reference results file")
argParser.add_argument("benchmark_config", help="Path to the benchmark config to use")
argParser.add_argument("-m", "--monitor-file-path", help="Path to the file containing the monitored resources on the reference run")
argParser.add_argument("-d", "--disk-io-limits", help="Path to the file containing the disk I/O limits to use")
argParser.add_argument("-i", "--initial-value", help="Value to use for cpu limitation on the first run")
argParser.add_argument("-r", "--max-runs", help="Maximum number of runs that will take place if threshold value is not hit")

class WorkloadType(Enum):
    """ Enum to represent the type of workload for the benchmark.
    """
    INSERTION = 1
    QUERY = 2
    MIXED = 3

def load_config(config_file_path: str) -> dict:
    """Load the benchmark configuration from a YAML file and determine the workload type.

    Args:
        config_file_path (str): Path to the benchmark configuration file.

    Returns:
        dict: A dictionary containing the workload type and the loaded configuration.
    """
    with open(config_file_path, 'r') as config_file:
        config = yaml.safe_load(config_file)
        
        # iterate over clients to find if workload is INSERTION, QUERY or MIXED
        insert = False
        query = False

        for _, client_config in config['benchmark_clients']['hosts'].items():
            if client_config['type'] == "INSERT":
                insert = True
            elif client_config['type'] == "QUERY":
                query = True

        if insert:
            workload_type = WorkloadType.INSERTION
        
        if query:
            workload_type = WorkloadType.QUERY if not insert else WorkloadType.MIXED

        return {"type": workload_type, "config": config}


def get_results_from_file(results_file_path: str) -> dict:
    """Parse the results file and extract relevant metrics.

    Args:
        results_file_path (str): Path to the results file.

    Returns:
        dict: A dictionary containing the parsed results with keys as metric 
        names and values as their corresponding values.
    """
    with open(results_file_path, 'r') as file:
        lines = file.readlines()
    
    results = {}
    level = 0

    cloud_line_flag = False
    global_flag = False

    last_header = ""
    for line in lines:
        if cloud_line_flag:
            if global_flag :

                if len(line) <= 1:
                    level = 0
                    continue

                if level > 0:
                    continue
                
                if HEADER_REGEX.match(line):
                    header = line.split(":")[0]
                    if header != "Latency breakdown":
                        level += 1
                    continue

                key, raw_value = line.split(": ")

                if "ops/s" in raw_value:
                    key += " ops"

                match = NUM_REGEX.match(raw_value)
                value = float(match.group(0))

                key_orig = key
                i = 1
                while key in results.keys():
                    key = f"{key_orig}_{i}"
                    i += 1

                results[key] = value

            elif "Global stats:" in line:
                global_flag = True
        elif "Cloud database node stats:" in line:
            cloud_line_flag = True
    
    return results 

def parse_disk_io_limts(filepath: str) -> dict:
    """Parse the disk I/O limits from a file.

    Args:
        filepath (str): Path to the file containing disk I/O limits.

    Returns:
        dict: A dictionary containing the disk I/O limits with keys as limit types
    """
    limits = {}

    with open(filepath, 'r') as file:
        lines = file.readlines()

    for line in lines:
        key, value = line.split(":")
        limits[key.strip()] = value.strip()

    return limits

def execute_test_run(config: dict, config_type: WorkloadType, server: dict, cpu_value: float):
    """ Execute a test run with the given configuration and server settings.

    Args:
        config (dict): Description of the configuration to use for the test run
        server (dict): Description of the server configuration to use for the test run
        cpu_value (float): The CPU value to set for the test run
    """

    server['limited_resources_cpu'] = cpu_value

    with open("test_config.yaml", "w") as test_config_file:
        yaml.dump(config, test_config_file, default_flow_style=False, allow_unicode=True)

    print("-- Performing Cleanup --")

    if config_type != WorkloadType.QUERY:
        subprocess.call(f"ansible-playbook {ANSIBLE_PATH}/shutdown-playbook.yaml -i test_config.yaml -t hard-reset", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True) 

    subprocess.call("docker rm --force mulletbench-orchestrator", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True)  

    print("-- Starting Run --")
    subprocess.call(f"ansible-playbook {ANSIBLE_PATH}/playbook.yaml -i test_config.yaml", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True)

    print("-- Orchestrator Logs --")
    os.system(f"docker logs --follow mulletbench-orchestrator  | tee {OUTPUT_PATH}optimize-run-{cpu_value}.txt")

def compare(reference_results: list[str], adjusted_results: list[str], key: str):
    return (adjusted_results[key] / reference_results[key]) - 1


def compare_results(reference_results: list[str], adjusted_results: list[str], config_type = WorkloadType.INSERTION) -> float:
    """ Compare the adjusted results with the reference results to determine the difference in performance.

    Args:
        reference_results (list[str]): Results of the reference run
        adjusted_results (list[str]): Results of the last adjusted test run
        config_type (_type_, optional): The type of the test run. Defaults to WorkloadType.INSERTION.

    Returns:
        float: The difference between the adjusted and reference results.
    """

    if config_type == WorkloadType.INSERTION: # INSERTION workload
        diff_rate = compare(reference_results, adjusted_results, INSERT_RATE)
        diff_latency = compare(reference_results, adjusted_results, INSERT_LATENCY)
        diff = (diff_rate + diff_latency) * 0.5
    elif config_type == WorkloadType.QUERY: # QUERY workload
        diff_rate = compare(reference_results, adjusted_results, QUERY_RATE)
        diff_latency = compare(reference_results, adjusted_results, QUERY_LATENCY)
        diff = (diff_rate + diff_latency) * 0.5
    else: # MIXED workload
        diff_insert = compare(reference_results, adjusted_results, INSERT_RATE) + compare(reference_results, adjusted_results, INSERT_LATENCY)
        diff_query = compare(reference_results, adjusted_results, QUERY_RATE) + compare(reference_results, adjusted_results, QUERY_LATENCY)
        diff = (diff_insert + diff_query) * 0.25

    return round(diff, MAX_DECIMAL_PLACES)

def calculate_next_value(current_cpu_value: float, reference_results: dict, adjusted_results: dict, prev_diff: float = -1, config_type = WorkloadType.INSERTION) -> float:
    """ Calculate the next CPU value based on the current CPU value and the difference between reference and adjusted results.

    Args:
        current_cpu_value (float): Current CPU value to adjust
        reference_results (dict): Results of the reference run
        adjusted_results (dict): Results of the last adjusted test run
        prev_diff (float, optional): Difference between the runs on the last adjustment. Defaults to -1.
        config_type (_type_, optional): The type of the test run. Defaults to WorkloadType.INSERTION.

    Returns:
        float: The next CPU value to use for the next test run.
    """

    divisor = 1

    diff = compare_results(reference_results, adjusted_results, config_type)

    if abs(diff) > 0.2 and abs(diff * 1.5) < 0.8:
        diff *= 1.5
    elif prev_diff != -1 and prev_diff * diff < 0:
        divisor *= 2

    if abs(diff) >= 0.8:
        diff = 0.8 if diff > 0 else -0.8

    return round(current_cpu_value - (diff * current_cpu_value) / divisor, MAX_DECIMAL_PLACES)


def monitor_df_from_path(file_path: str) -> pd.DataFrame:
    """ Load the monitor data from a CSV file and format the columns.

    Args:
        file_path (str): Path to the CSV file containing monitor data.

    Returns:
        pd.DataFrame: A DataFrame containing the formatted monitor data with appropriate column names.
    """

    def get_interface_columns(monitor_df):
        interface_re = re.compile(r"network\.interface\.(in|out)\.bytes-(.*)")
        interface_columns = []
        for column in range(0,4):
            matched = interface_re.match(monitor_df.columns[column+1])
            interface_columns.append(matched.group(2) + "-" + matched.group(1))
        
        return interface_columns

    monitor_df = pd.read_csv(file_path)

    interface_columns = get_interface_columns(monitor_df)

    monitor_df.columns = ['time', interface_columns[0], interface_columns[1], interface_columns[2], interface_columns[3], 'RAM', 'cpu-system', 'cpu-user', 'io-read', 'io-write']

    return monitor_df

def main():
    global OUTPUT_PATH, MAX_RUNS
    
    args = argParser.parse_args() 

    if not OUTPUT_PATH.endswith("/"):
        OUTPUT_PATH += "/"
    
    if not os.path.exists(OUTPUT_PATH):
        os.makedirs(OUTPUT_PATH)

    loaded_config = load_config(args.benchmark_config)
    # print(loaded_config["type"])

    # load edge servers from config
    edge_servers: dict = loaded_config["config"]['edgeservers']['hosts']

    if len(edge_servers) > 1:
        print("Config File should have only 1 edge server")
        exit(1)

    server = edge_servers[list(edge_servers.keys())[0]]
    reference_results = get_results_from_file(args.reference_results)

    if args.max_runs is not None:
        MAX_RUNS = int(args.max_runs)

    if args.monitor_file_path is not None:
        df = monitor_df_from_path(args.monitor_file_path)
        server['limited_resources_mem'] = int(df['RAM'].max())

    if args.disk_io_limits is not None:
        io_limits = parse_disk_io_limts(args.disk_io_limits)

        for key, value in io_limits.items():
            server[f'limited_resources_{key}'] = value

    # print(server)

    num_runs = 0
    prev_diff = -1
    cpu_value = INITIAL_VALUE
    if args.initial_value is not None:
        cpu_value = float(args.initial_value)

    while num_runs < MAX_RUNS:

        print(f"\n\n\n\nTest Run with {cpu_value = }\n")

        execute_test_run(loaded_config["config"],  loaded_config['type'], server, cpu_value)

        run_results = get_results_from_file(f"{OUTPUT_PATH}optimize-run-{cpu_value}.txt")

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        # print(f"\nreferece_insert_rate / run_insert_rate = {comparation}\n")
        print(f"\tValue of difference: {comparation}")

        if  abs(comparation) < STOP_THRESHOLD:
            print(f"Optimal value for CPU is {cpu_value}")
            break

        cpu_value = calculate_next_value(cpu_value, reference_results, run_results, prev_diff, loaded_config['type'])

        num_runs += 1
        prev_diff = comparation

if __name__ == "__main__":
    main()

