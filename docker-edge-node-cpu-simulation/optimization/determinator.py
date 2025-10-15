from config import config
from io_utils.file_parsing import get_results_from_file
from ansible_runner.executor import execute_test_run
from compare import compare_results
from adjust_cpu import calculate_next_value_cpu
from adjust_disk_io import calculate_next_value_disk_io
import json

def run_determination_test_alternate(args, loaded_config: dict, server: dict, reference_results: dict, io_limits: dict) -> tuple[float, dict]:
    num_runs = 0
    cpu_value = config.INITIAL_VALUE
    disk_adjusts = 0
    optimal = False
    last = "disk"
    if args.initial_value is not None:
        cpu_value = float(args.initial_value)

    results = []
    last_adjust = 2

    while num_runs < config.MAX_RUNS:

        current_config_str = json.dumps({"cpu_limit": cpu_value, "io_limits": io_limits}, indent=4)
        print(f"\n\n\n\nTest Run with {current_config_str}\n")

        execute_test_run(loaded_config["config"],  loaded_config['type'], server, cpu_value, io_limits, disk_adjusts)

        run_results = get_results_from_file(f"{config.OUTPUT_PATH}optimize-run-{cpu_value}-disk-adjusts-{disk_adjusts}.txt")

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        print(f"\tValue of difference: {comparation}")

        if  abs(comparation) < config.STOP_THRESHOLD:
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
            io_limits = calculate_next_value_disk_io(io_limits, config.INITIAL_DISK_IO, reference_results, run_results, loaded_config['type'])
            disk_adjusts += 1
            for key, value in io_limits.items():
                server[f'limited_resources_{key}'] = value
            last = "disk"

        last_adjust -= 1

        if last_adjust == 0:
            config.MAX_ADJUST_CPU = config.MAX_ADJUST_CPU / config.ADJUST_DIVISOR_CPU
            if config.PRINT_DEBUG:
                print(f"\n\n--- {config.MAX_ADJUST_CPU = } ---")
            last_adjust = 2

        num_runs += 1

    if not optimal:
        print(f"Could not find optimal value in {config.MAX_RUNS} runs")
        best_run = min(results, key=lambda x: abs(x['diff']))
        print(f"Best run was: { json.dumps(best_run, indent=4) }")

        return best_run['cpu'], best_run['io']

    return cpu_value, io_limits


def run_determination_test_cpu(args, loaded_config: dict, server: dict, reference_results: dict, io_limits: dict, calculate_disk_io: bool = False) -> tuple[float, dict]:
    num_runs = 0
    cpu_value = config.INITIAL_VALUE
    optimal = False
    if args.initial_value is not None:
        cpu_value = float(args.initial_value)

    results = []
    last_adjust = 1

    while num_runs < config.MAX_RUNS:

        print(f"\n\n\n\nTest Run with {cpu_value = }\n")

        execute_test_run(loaded_config["config"],  loaded_config['type'], server, cpu_value, io_limits)

        run_results = get_results_from_file(f"{config.OUTPUT_PATH}optimize-run-{cpu_value}.txt")

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        print(f"\tValue of difference: {comparation}")

        if  abs(comparation) < config.STOP_THRESHOLD:
            print(f"Optimal value for CPU is {cpu_value}")
            optimal = True
            break
        else:
            results.append({
                "cpu": cpu_value,
                "diff": comparation
            })

        cpu_value = calculate_next_value_cpu(cpu_value, reference_results, run_results, loaded_config['type'])

        last_adjust -= 1

        if last_adjust == 0:
            config.MAX_ADJUST_CPU = config.MAX_ADJUST_CPU / config.ADJUST_DIVISOR_CPU
            if config.PRINT_DEBUG:
                print(f"\n\n--- {config.MAX_ADJUST_CPU = } ---")
            last_adjust = 1

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
    
    return cpu_value, io_limits


def run_determination_test_disk_io(args, loaded_config: dict, server: dict, reference_results: dict, io_limits: dict) -> tuple[dict, bool]:
    config.STOP_THRESHOLD = 0.02

    num_runs = 0
    disk_adjusts = 0
    optimal = False

    results = []

    while num_runs < config.MAX_RUNS:

        print(f"\n\n\n\nTest Run with {io_limits = }\n")

        execute_test_run(loaded_config["config"],  loaded_config['type'], server, server.get('limited_resources_cpu', 1), io_limits, disk_adjusts)

        run_results = get_results_from_file(f"{config.OUTPUT_PATH}optimize-run-{server.get('limited_resources_cpu', 1)}-disk-adjusts-{disk_adjusts}.txt")

        comparation = compare_results(reference_results, run_results, loaded_config['type'])

        print(f"\tValue of difference: {comparation}")

        if  abs(comparation) < config.STOP_THRESHOLD:
            print(f"Optimal value for Disk I/O is {json.dumps(io_limits, indent=4)}")
            optimal = True
            break
        else:
            results.append({
                "io": io_limits,
                "diff": comparation
            })

        io_limits = calculate_next_value_disk_io(io_limits, config.INITIAL_DISK_IO, reference_results, run_results, loaded_config['type'])
        disk_adjusts += 1
        for key, value in io_limits.items():
            server[f'limited_resources_{key}'] = value

        num_runs += 1

    if not optimal:
        print(f"Could not find optimal value in {config.MAX_RUNS} runs")
        best_run = min(results, key=lambda x: abs(x['diff']))
        print(f"Best run was: { json.dumps(best_run, indent=4) }")

        return best_run, False

    return io_limits, True
