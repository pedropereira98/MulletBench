import time
from config import config
from state import state
from io_utils.file_parsing import get_results_from_run
from ansible_runner.executor import execute_test_run
from optimization.compare import compare_results
from optimization.adjust_cpu import calculate_next_value_cpu, append_cpu_history
from optimization.adjust_disk_io import calculate_next_value_disk_io, multiply_disk_io, append_disk_io_history
from optimization.adjust_alternate import axis_to_values, calculate_axis, calculate_next_value_alternate, append_alternate_history
import json

def run_determination_test_alternate(args, loaded_config: dict, server: dict, reference_results: dict, io_limits: dict) -> tuple[float, dict]:
    num_runs = 0
    cpu_value = config.INITIAL_CPU_VALUE
    io_multiplier = config.INITIAL_DISK_MULTIPLIER
    optimal = False
    last = "disk"
    results = []
    bias = 0

    while num_runs < config.MAX_RUNS:

        io_limits = multiply_disk_io(config.INITIAL_DISK_IO, io_multiplier)
        
        current_config_str = json.dumps({"cpu_limit": cpu_value, "io_limits": io_limits}, indent=4)
        print(f"\n\n\n\nTest Run with {current_config_str}\n")

        test_name = execute_test_run(loaded_config["config"],  loaded_config['type'], server, cpu_value, io_limits, io_multiplier)

        # run_results = get_results_from_file(f"{config.OUTPUT_PATH}optimize-run-{cpu_value}-disk-adjusts-{disk_adjusts}.txt")
        run_results = get_results_from_run(test_name)

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        print(f"\tValue of difference: {comparation}")

        if  abs(comparation) < config.STOP_THRESHOLD:
            if io_multiplier >= 0.95:
                print(f"Optimal value for resources found: \n{current_config_str}\n, but disk isn't adjusted. Exploring further configurations..")
                
                # bias towards CPU -> get lower disk values
                bias = 2
                cpu_value, io_multiplier = axis_to_values(calculate_axis(cpu_value, io_multiplier), (cpu_value, io_multiplier), bias)
            else:
                print(f"Optimal value for resources is:\n{current_config_str}")
                optimal = True
                append_alternate_history(cpu_value, io_multiplier, bias, comparation)
                break
        else:
            results.append({
                "cpu": cpu_value,
                "io": io_limits,
                "diff": comparation
            })

            cpu_value, io_multiplier, bias = calculate_next_value_alternate(cpu_value, io_multiplier, bias, last=="disk", reference_results, run_results, loaded_config["type"])
        
        last = last == "disk" and "cpu" or "disk"
        
        num_runs += 1
        
    if not optimal:
        print(f"Could not find optimal value in {config.MAX_RUNS} runs")
        best_run = min(results, key=lambda x: abs(x['diff']))
        print(f"Best run was: { json.dumps(best_run, indent=4) }")

        return best_run['cpu'], best_run['io']

    return cpu_value, io_limits


def run_determination_test_cpu(args, loaded_config: dict, server: dict, reference_results: dict, io_limits: dict, calculate_disk_io: bool = False) -> tuple[float, dict]:
    num_runs = 0
    cpu_value = config.INITIAL_CPU_VALUE
    optimal = False
    results = []

    while num_runs < config.MAX_RUNS:

        print(f"\n\n\n\nTest Run with {cpu_value = }\n")

        test_name = execute_test_run(loaded_config["config"],  loaded_config['type'], server, cpu_value, io_limits)

        # run_results = get_results_from_file(f"{config.OUTPUT_PATH}optimize-run-{cpu_value}.txt")
        run_results = get_results_from_run(test_name)

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        print(f"\n\tValue of difference: {comparation}")

        if  abs(comparation) < config.STOP_THRESHOLD:
            print(f"Optimal value for CPU is {cpu_value}")
            optimal = True
            append_cpu_history(cpu_value, comparation)
            break
        else:
            results.append({
                "cpu": cpu_value,
                "diff": comparation
            })

        cpu_value = calculate_next_value_cpu(cpu_value, reference_results, run_results, loaded_config['type'])

        num_runs += 1

    best_run = cpu_value

    if not optimal:
        print(f"Could not find optimal value for cpu in {config.MAX_RUNS} runs")
        best_run = min(results, key=lambda x: abs(x['diff']))
        print(f"Best run was: { json.dumps(best_run, indent=4) }")

    if calculate_disk_io:
        print("Adjusting Disk I/O limits now")

        best_io, optimal_io = run_determination_test_disk_io(args, loaded_config, server, reference_results, io_limits)

        if optimal_io:
            print(f"Optimal Disk I/O limits found: {json.dumps(best_io, indent=4)}")
    
        return best_run, best_io
    
    return best_run, io_limits


def run_determination_test_disk_io(args, loaded_config: dict, server: dict, reference_results: dict, io_limits: dict) -> tuple[dict, bool]:
    config.STOP_THRESHOLD = 0.02

    num_runs = 0
    optimal = False
    io_multiplier = config.INITIAL_DISK_MULTIPLIER

    results = []

    while num_runs < config.MAX_RUNS:
        
        io_limits = multiply_disk_io(config.INITIAL_DISK_IO, io_multiplier)

        print(f"\n\n\n\nTest Run with {io_limits = }\n")

        test_name = execute_test_run(loaded_config["config"],  loaded_config['type'], server, server.get('limited_resources_cpu', 1), io_limits, io_multiplier)

        # run_results = get_results_from_file(f"{config.OUTPUT_PATH}optimize-run-{server.get('limited_resources_cpu', 1)}-disk-adjusts-{disk_adjusts}.txt")
        run_results = get_results_from_run(test_name)

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        print(f"\tValue of difference: {comparation}")

        if  abs(comparation) < config.STOP_THRESHOLD:
            print(f"Optimal value for Disk I/O is {json.dumps(io_limits, indent=4)}")
            optimal = True
            append_disk_io_history(io_multiplier, comparation)
            break
        else:
            results.append({
                "io": io_limits,
                "diff": comparation
            })

        io_multiplier = calculate_next_value_disk_io(io_multiplier, reference_results, run_results, loaded_config['type'])


        num_runs += 1

    if not optimal:
        print(f"Could not find optimal value in {config.MAX_RUNS} runs")
        best_run = min(results, key=lambda x: abs(x['diff']))
        print(f"Best run was: { json.dumps(best_run, indent=4) }")

        return best_run, False

    return io_limits, True
