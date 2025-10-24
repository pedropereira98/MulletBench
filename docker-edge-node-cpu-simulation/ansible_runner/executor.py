from config import config
from workload import WorkloadType
import subprocess
import yaml
import os
import shutil


def execute_test_run(test_config: dict, config_type: WorkloadType, server: dict, cpu_value: float , io_limits: dict = {}, disk_adjusts: int = -1) ->  str:
    """ Execute a test run with the given configuration and server settings.

    Args:
        config (dict): Description of the configuration to use for the test run
        server (dict): Description of the server configuration to use for the test run
        cpu_value (float): The CPU value to set for the test run

    Returns:
        str: Name of the test 
    """

    server['limited_resources_cpu'] = cpu_value

    for key, value in io_limits.items():
        server[f'limited_resources_{key}'] = value

    with open("test_config.yaml", "w") as test_config_file:
        yaml.dump(test_config, test_config_file, default_flow_style=False, allow_unicode=True)


    test_name = ""
    if disk_adjusts != -1:
        test_name = f"run-{cpu_value}-disk-adjusts-{disk_adjusts}"
    else:
        test_name = f"run-{cpu_value}"

    output_dir = os.path.join(config.OUTPUT_PATH, test_name)
    
    if os.path.exists(output_dir) and len(os.listdir(output_dir)) != 0:
        shutil.rmtree(output_dir)
    os.makedirs(output_dir, exist_ok=True)


    for i in range(config.RUNS_PER_ADJUST):

        print("-- Performing Cleanup --")

        if config_type != WorkloadType.QUERY:
            if config.PRINT_DEBUG:
                subprocess.call(f"ansible-playbook {config.ANSIBLE_PATH}/shutdown-playbook.yaml -i test_config.yaml -t hard-reset", shell=True)
            else:
                subprocess.call(f"ansible-playbook {config.ANSIBLE_PATH}/shutdown-playbook.yaml -i test_config.yaml -t hard-reset", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True) 

        subprocess.call("docker rm --force mulletbench-orchestrator", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True)  

        run = f"run-{i+1}.txt"
        print(f"-- Starting Run {i+1:2} --")
        if config.PRINT_DEBUG:
            subprocess.call(f"ansible-playbook {config.ANSIBLE_PATH}/playbook.yaml -i test_config.yaml", shell=True)
        else:
            subprocess.call(f"ansible-playbook {config.ANSIBLE_PATH}/playbook.yaml -i test_config.yaml", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True)

        print("-- Orchestrator Logs --")
        os.system(f"docker logs --follow mulletbench-orchestrator  | tee {os.path.join(output_dir, run)}")

    return test_name
