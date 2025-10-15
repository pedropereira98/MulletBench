import yaml
from enum import Enum

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