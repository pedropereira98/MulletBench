import argparse

def parse_arguments():
    argParser = argparse.ArgumentParser(prog="optimize_docker_cpu.py")
    argParser.add_argument("reference_results", help="Path to the reference results file")
    argParser.add_argument("benchmark_config", help="Path to the benchmark config to use")
    argParser.add_argument("-m", "--monitor-file-path", help="Path to the file containing the monitored resources on the reference run")
    argParser.add_argument("-d", "--disk-io-limits", help="Path to the file containing the disk I/O limits to use. These values will " \
                                                            "be used as the initial and maximum values for the disk I/O limits")
    argParser.add_argument("-i", "--initial-cpu-value", help="Value to use for cpu limitation on the first run")
    argParser.add_argument("--initial-disk-multiplier", help="Value to use for disk I/O multiplier on the first run")
    argParser.add_argument("-r", "--max-runs", help="Maximum number of runs that will take place if threshold value is not hit")
    argParser.add_argument("--cpu-only", action='store_true',  help="Only adjust CPU limits, do not adjust Disk I/O limits")
    argParser.add_argument("--alternate", action='store_true',  help="Alternate between adjusting CPU and Disk I/O limits")
    argParser.add_argument("--disk-only", action='store_true', help="Only adjust Disk I/O limits, do not adjust CPU limits")
    argParser.add_argument("--debug", action='store_true', help="Print debug information")
    
    return argParser.parse_args()