from config import config
from cli import parse_arguments
from workload import load_config
from io_utils.file_parsing import get_results_from_file, parse_disk_io_limts
from io_utils.monitor import monitor_df_from_path
from optimization.determinator import run_determination_test_cpu, run_determination_test_disk_io, run_determination_test_alternate
import json
import os

def main():

    args = parse_arguments()

    if not config.OUTPUT_PATH.endswith("/"):
        config.OUTPUT_PATH += "/"

    if not os.path.exists(config.OUTPUT_PATH):
        os.makedirs(config.OUTPUT_PATH)

    if args.debug is not None and args.debug:
        print(f"Debug mode is on")
        config.PRINT_DEBUG = True

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
        config.MAX_RUNS = int(args.max_runs)

    if args.monitor_file_path is not None:
        df = monitor_df_from_path(args.monitor_file_path)
        server['limited_resources_mem'] = int(df['RAM'].max())

    if args.disk_io_limits is not None:
        io_limits = parse_disk_io_limts(args.disk_io_limits)
        config.INITIAL_DISK_IO = io_limits.copy()

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
        config.STOP_THRESHOLD = 0.05
        best_cpu, best_io = run_determination_test_cpu(args, loaded_config, server, reference_results, io_limits, True)

    cpu_result = "Not Adjusted"
    if cpu_result is not None:
        cpu_result = best_cpu

        cpu_history_str = json.dumps([{"cpu":cpu, "diff":diff, "timestamp":ts} for (cpu, diff, ts) in filter(lambda x: x[0:2], sorted(config.CPU_HISTORY, key=lambda x: x[2]))], indent=4)
        print(f"\n\nCPU Adjust History:\n{cpu_history_str}")

    io_result = "Not adjusted"
    if best_io is not None:
        io_result = "\n".join([f"{key}: {value}" for key, value in best_io.items()])

        disk_history_str = json.dumps([{"io_val":io_val, "diff":diff, "timestamp":ts} for (io_val, diff, ts) in filter(lambda x: x[0:2], sorted(config.DISK_IO_HISTORY, key=lambda x: x[2]))], indent=4)
        print(f"\n\nDisk I/O Adjust History:\n{disk_history_str}")

    print(f"\n\n\nFinal results are:\n"
        f"CPU: {best_cpu if best_cpu is not None else 'Not adjusted'}\n"
        f"Disk I/O:\n\t{io_result}")

if __name__ == "__main__":
    main()

