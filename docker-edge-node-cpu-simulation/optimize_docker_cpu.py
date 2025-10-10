
import json
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
PRINT_DEBUG = False

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
DISK_BPS_REGEX = re.compile(r"(\d+\.\d+)\s*(M|G)")

CPU_HISTORY = []
DISK_IO_HISTORY = []

argParser = argparse.ArgumentParser(prog="optimize_docker_cpu.py")
argParser.add_argument("reference_results", help="Path to the reference results file")
argParser.add_argument("benchmark_config", help="Path to the benchmark config to use")
argParser.add_argument("-m", "--monitor-file-path", help="Path to the file containing the monitored resources on the reference run")
argParser.add_argument("-d", "--disk-io-limits", help="Path to the file containing the disk I/O limits to use")
argParser.add_argument("-i", "--initial-value", help="Value to use for cpu limitation on the first run")
argParser.add_argument("-r", "--max-runs", help="Maximum number of runs that will take place if threshold value is not hit")
argParser.add_argument("--cpu-only", help="Only adjust CPU limits, do not adjust Disk I/O limits")
argParser.add_argument("--alternate", help="Alternate between adjusting CPU and Disk I/O limits")
argParser.add_argument("--disk-only", help="Only adjust Disk I/O limits, do not adjust CPU limits")
argParser.add_argument("--debug", action='store_true', help="Print debug information")

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

def execute_test_run(config: dict, config_type: WorkloadType, server: dict, cpu_value: float , io_limits: dict = {}, disk_adjusts: int = -1):
    """ Execute a test run with the given configuration and server settings.

    Args:
        config (dict): Description of the configuration to use for the test run
        server (dict): Description of the server configuration to use for the test run
        cpu_value (float): The CPU value to set for the test run
    """

    server['limited_resources_cpu'] = cpu_value

    for key, value in io_limits.items():
        server[f'limited_resources_{key}'] = value

    with open("test_config.yaml", "w") as test_config_file:
        yaml.dump(config, test_config_file, default_flow_style=False, allow_unicode=True)

    print("-- Performing Cleanup --")

    if config_type != WorkloadType.QUERY:
        if PRINT_DEBUG:
            subprocess.call(f"ansible-playbook {ANSIBLE_PATH}/shutdown-playbook.yaml -i test_config.yaml -t hard-reset", shell=True)
        else:
            subprocess.call(f"ansible-playbook {ANSIBLE_PATH}/shutdown-playbook.yaml -i test_config.yaml -t hard-reset", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True) 

    subprocess.call("docker rm --force mulletbench-orchestrator", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True)  

    print("-- Starting Run --")
    if PRINT_DEBUG:
        subprocess.call(f"ansible-playbook {ANSIBLE_PATH}/playbook.yaml -i test_config.yaml", shell=True)
    else:
        subprocess.call(f"ansible-playbook {ANSIBLE_PATH}/playbook.yaml -i test_config.yaml", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True)

    print("-- Orchestrator Logs --")
    if disk_adjusts != -1:
        os.system(f"docker logs --follow mulletbench-orchestrator  | tee {OUTPUT_PATH}optimize-run-{cpu_value}-disk-adjusts-{disk_adjusts}.txt")
    else:
        os.system(f"docker logs --follow mulletbench-orchestrator  | tee {OUTPUT_PATH}optimize-run-{cpu_value}.txt")

def compare(reference_results: list[str], adjusted_results: list[str], key: str):
    """ Compare a specific metric between the reference and adjusted results.

    Args:
        reference_results (list[str]): Results of the reference run
        adjusted_results (list[str]): Results of the last adjusted test run
        key (str): The specific metric to compare

    Returns:
        float: The difference between the adjusted and reference results for the specified metric.
    """
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
        diff_latency = compare(adjusted_results, reference_results, INSERT_LATENCY)
        diff = (diff_rate + diff_latency) * 0.5
    elif config_type == WorkloadType.QUERY: # QUERY workload
        diff_rate = compare(reference_results, adjusted_results, QUERY_RATE)
        # diff_latency = compare(adjusted_results, reference_results, QUERY_LATENCY)
        # diff = (diff_rate + diff_latency) * 0.5
        diff = diff_rate
    else: # MIXED workload
        metrics_rate = [INSERT_RATE, QUERY_RATE]
        metrics_latency = [INSERT_LATENCY, QUERY_LATENCY]
        
        diff_rate = sum([compare(reference_results, adjusted_results, metric) for metric in metrics_rate]) / len(metrics_rate)
        diff_latency = sum([compare(adjusted_results, reference_results, metric) for metric in metrics_latency]) / len(metrics_latency)
        diff = (diff_rate + diff_latency) / 2
        # diff_insert = compare(reference_results, adjusted_results, INSERT_RATE) + compare(adjusted_results, reference_results, INSERT_LATENCY)
        # diff_query = compare(reference_results, adjusted_results, QUERY_RATE) + compare(adjusted_results, reference_results, QUERY_LATENCY)
        # diff = (diff_insert + diff_query) / 4

    return round(diff, MAX_DECIMAL_PLACES)

def calculate_next_value_cpu(current_cpu_value: float, reference_results: dict, adjusted_results: dict, config_type = WorkloadType.INSERTION) -> float:
    """ Calculate the next CPU value based on the current CPU value and the difference between reference and adjusted results.

    Args:
        current_cpu_value (float): Current CPU value to adjust
        reference_results (dict): Results of the reference run
        adjusted_results (dict): Results of the last adjusted test run
        config_type (_type_, optional): The type of the test run. Defaults to WorkloadType.INSERTION.

    Returns:
        float: The next CPU value to use for the next test run.
    """
    global CPU_HISTORY

    divisor = 1

    diff = compare_results(reference_results, adjusted_results, config_type)

    if abs(diff) >= 0.8:
        diff = 0.8 if diff > 0 else -0.8

    CPU_HISTORY.append((current_cpu_value, diff))
    CPU_HISTORY.sort(key=lambda x: x[0])

    lower = upper = None

    for i in range(1, len(CPU_HISTORY)):
        val1, val2 = CPU_HISTORY[i-1], CPU_HISTORY[i]
        if val1[1] * val2[1] < 0:
            lower, upper = val1, val2
            break

    if lower and upper:
        weight_low = 1 / abs(lower[1])
        weight_up = 1 / abs(upper[1])
        next_cpu = (lower[0] * weight_low + upper[0] * weight_up) / (weight_low + weight_up)
    else:
        
        next_cpu = current_cpu_value - (diff * current_cpu_value) / divisor

    return round(next_cpu, MAX_DECIMAL_PLACES)

def calculate_next_value_disk_io(current_io_value: dict[str, float], reference_results: dict, adjusted_results: dict, config_type = WorkloadType.INSERTION) -> dict[str, float]:
    """ Calculate the next Disk I/O value based on the current Disk I/O value and the difference between reference and adjusted results.

    Args:
        current_io_value (dict[str, float]): Current Disk I/O values to adjust
        reference_results (dict): Results of the reference run
        adjusted_results (dict): Results of the last adjusted test run
        config_type (_type_, optional): The type of the test run. Defaults to WorkloadType.INSERTION.

    Returns:
        dict[str, float]: The next Disk I/O values to use for the next test run.
    """
    global DISK_IO_HISTORY
    
    divisor = 1

    diff = compare_results(reference_results, adjusted_results, config_type)

    if abs(diff) >= 0.8:
        diff = 0.8 if diff > 0 else -0.8

    DISK_IO_HISTORY.append((current_io_value, diff))
    DISK_IO_HISTORY.sort(key=lambda x: int(x[0]['write_iops']))

    lower = upper = None

    for i in range(1, len(DISK_IO_HISTORY)):
        val1, val2 = DISK_IO_HISTORY[i-1], DISK_IO_HISTORY[i]
        if val1[1] * val2[1] < 0:
            lower, upper = val1, val2

    next_io_values = {}

    if lower and upper:
        weight_low = 1 / abs(lower[1])
        weight_up = 1 / abs(upper[1])

        for (io1, io2) in zip(lower[0].items(), upper[0].items()):
            k1, v1 = io1
            _, v2 = io2

            if "iops" in k1:
                v1 = int(v1)
                v2 = int(v2)
                v = (weight_low * v1 + weight_up * v2) / (weight_low + weight_up)
                next_io_values[k1] = str(round(v))
            else:
                num1, unit1 = DISK_BPS_REGEX.match(v1).groups()
                num2, _ = DISK_BPS_REGEX.match(v2).groups()
                v1 = float(num1)
                v2 = float(num2)
                v = (weight_low * v1 + weight_up * v2) / (weight_low + weight_up)
                next_io_values[k1] = f"{round(v, MAX_DECIMAL_PLACES)}{unit1}"
    else:

        for k, val in current_io_value.items():
            if "iops" in k:
                val = int(val)
                next_io_values[k] = str(round(val - (val * diff) / divisor))
            else:
                num, unit = DISK_BPS_REGEX.match(val).groups()
                num = float(num)
                next_io_values[k] = f"{round(num - (num * diff) / divisor, MAX_DECIMAL_PLACES)}{unit}"

    return next_io_values


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


def run_determination_test_alternate(args, loaded_config: dict, server: dict, reference_results: dict, io_limits: dict) -> tuple[float, dict]:

    num_runs = 0
    cpu_value = INITIAL_VALUE
    disk_adjusts = 0
    optimal = False
    last = "disk"
    if args.initial_value is not None:
        cpu_value = float(args.initial_value)

    results = []

    while num_runs < MAX_RUNS:

        print(f"\n\n\n\nTest Run with {cpu_value = }\n")

        execute_test_run(loaded_config["config"],  loaded_config['type'], server, cpu_value)

        run_results = get_results_from_file(f"{OUTPUT_PATH}optimize-run-{cpu_value}-disk-adjusts-{disk_adjusts}.txt")

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        print(f"\tValue of difference: {comparation}")

        if  abs(comparation) < STOP_THRESHOLD:
            print(f"Optimal value for CPU is {cpu_value}")
            optimal = True
            break
        else:
            results.append({
                "cpu": cpu_value,
                "io": io_limits,
                "diff": comparation
            })

        if last == "disk":
            cpu_value = calculate_next_value_cpu(cpu_value, reference_results, run_results, loaded_config['type'])
            last = "cpu"
        else:
            io_limits = calculate_next_value_disk_io(io_limits, reference_results, run_results, loaded_config['type'])
            disk_adjusts += 1
            for key, value in io_limits.items():
                server[f'limited_resources_{key}'] = value
            last = "disk"

        num_runs += 1

    if not optimal:
        print(f"Could not find optimal value in {MAX_RUNS} runs")
        best_run = min(results, key=lambda x: abs(x['diff']))
        print(f"Best run was: { json.dumps(best_run, indent=4) }")

        return best_run['cpu'], best_run['io']

    return cpu_value, io_limits


def run_determination_test_cpu(args, loaded_config: dict, server: dict, reference_results: dict, io_limits: dict, calculate_disk_io: bool = False) -> tuple[float, dict]:

    num_runs = 0
    cpu_value = INITIAL_VALUE
    optimal = False
    if args.initial_value is not None:
        cpu_value = float(args.initial_value)

    results = []

    while num_runs < MAX_RUNS:

        print(f"\n\n\n\nTest Run with {cpu_value = }\n")

        execute_test_run(loaded_config["config"],  loaded_config['type'], server, cpu_value, io_limits)

        run_results = get_results_from_file(f"{OUTPUT_PATH}optimize-run-{cpu_value}.txt")

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        print(f"\tValue of difference: {comparation}")

        if  abs(comparation) < STOP_THRESHOLD:
            print(f"Optimal value for CPU is {cpu_value}")
            optimal = True
            break
        else:
            results.append({
                "cpu": cpu_value,
                "diff": comparation
            })

        cpu_value = calculate_next_value_cpu(cpu_value, reference_results, run_results, loaded_config['type'])

        num_runs += 1

    if not optimal:
        print(f"Could not find optimal value in {MAX_RUNS} runs")
        best_run = min(results, key=lambda x: abs(x['diff']))
        print(f"Best run was: { json.dumps(best_run, indent=4) }")

    if calculate_disk_io:
        print("Adjusting Disk I/O limits now")

        best_io, optimal_io = run_determination_test_disk_io(args, loaded_config, server, reference_results, io_limits)

        if optimal_io:
            print(f"Optimal Disk I/O limits found: {json.dumps(best_io, indent=4)}")
    
        return best_run, best_io
    
    return cpu_value, io_limits


def run_determination_test_disk_io(args, loaded_config: dict, server: dict, reference_results: dict, io_limits: dict) -> tuple[dict, bool]:
    global STOP_THRESHOLD

    STOP_THRESHOLD = 0.02

    num_runs = 0
    disk_adjusts = 0
    optimal = False

    results = []

    while num_runs < MAX_RUNS:

        print(f"\n\n\n\nTest Run with {io_limits = }\n")

        execute_test_run(loaded_config["config"],  loaded_config['type'], server, server.get('limited_resources_cpu', 1), io_limits, disk_adjusts)

        run_results = get_results_from_file(f"{OUTPUT_PATH}optimize-run-{server.get('limited_resources_cpu', 1)}-disk-adjusts-{disk_adjusts}.txt")

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        print(f"\tValue of difference: {comparation}")

        if  abs(comparation) < STOP_THRESHOLD:
            print(f"Optimal value for Disk I/O is {json.dumps(io_limits, indent=4)}")
            optimal = True
            break
        else:
            results.append({
                "io": io_limits,
                "diff": comparation
            })

        io_limits = calculate_next_value_disk_io(io_limits, reference_results, run_results, loaded_config['type'])
        disk_adjusts += 1
        for key, value in io_limits.items():
            server[f'limited_resources_{key}'] = value

        num_runs += 1

    if not optimal:
        print(f"Could not find optimal value in {MAX_RUNS} runs")
        best_run = min(results, key=lambda x: abs(x['diff']))
        print(f"Best run was: { json.dumps(best_run, indent=4) }")

        return best_run, False

    return io_limits, True




def main():
    global OUTPUT_PATH, MAX_RUNS, STOP_THRESHOLD, PRINT_DEBUG
    
    args = argParser.parse_args() 

    if not OUTPUT_PATH.endswith("/"):
        OUTPUT_PATH += "/"
    
    if not os.path.exists(OUTPUT_PATH):
        os.makedirs(OUTPUT_PATH)

    if args.debug is not None and args.debug:
        print(f"Debug mode is on")
        PRINT_DEBUG = True

    loaded_config = load_config(args.benchmark_config)

    print(f"Workload is of type {loaded_config['type'].name}")    

    # load edge servers from config
    edge_servers: dict = loaded_config["config"]['edgeservers']['hosts']

    if len(edge_servers) > 1:
        print("Config File should have only 1 edge server")
        exit(1)

    server = edge_servers[list(edge_servers.keys())[0]]
    io_limits = {}
    io_limits['limited_resources_read_bps'] = server.get('limited_resources_read_bps', None)
    io_limits['limited_resources_write_bps'] = server.get('limited_resources_write_bps', None)
    io_limits['limited_resources_read_iops'] = server.get('limited_resources_read_iops', None)
    io_limits['limited_resources_write_iops'] = server.get('limited_resources_write_iops', None)


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
    best_cpu, best_io = None, None

    if args.cpu_only:
        best_cpu, _ = run_determination_test_cpu(args, loaded_config, server, reference_results, io_limits)
    elif args.disk_only:
        best_io, _ =run_determination_test_disk_io(args, loaded_config, server, reference_results, io_limits)
    elif args.alternate:
        best_cpu, best_io = run_determination_test_alternate(args, loaded_config, server, reference_results, io_limits)
    else:
        STOP_THRESHOLD = 0.05
        best_cpu, best_io = run_determination_test_cpu(args, loaded_config, server, reference_results, io_limits, True)

    cpu_result = "Not Adjusted"
    if cpu_result is not None:
        cpu_result = best_cpu

    io_result = "Not adjusted"
    if best_io is not None:
        io_result = "\n".join([f"{key}: {value}" for key, value in best_io.items()])

    print(f"\n\n\nFinal results are:\n"
        f"CPU: {best_cpu if best_cpu is not None else 'Not adjusted'}\n"
        f"Disk I/O:\n\t{io_result}")

if __name__ == "__main__":
    main()

