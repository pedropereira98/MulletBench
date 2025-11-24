import re

class Config:

    def __init__(self):

        self.ANSIBLE_PATH = "~/MulletBench/ansible" # Path to the Ansible playbook directory
        self.OUTPUT_PATH = "outputs/" # Path to the output directory where results will be stored

        self.RUNS_PER_ADJUST = 3
        self.MAX_RUNS = 10
        self.STOP_THRESHOLD = 0.02
        self.MAX_DECIMAL_PLACES = 5
        self.INITIAL_VALUE = 1
        self.INITIAL_DISK_IO = {}
        self.INITIAL_ADJUST_CPU = 0.5
        self.MAX_ADJUST_CPU = 0.5
        self.ADJUST_DIVISOR_CPU = 2
        self.MIN_CPU_VALUE = 0.15
        self.FAILED_OPERATION_MARGIN = 0.2
        self.DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
        self.PRINT_DEBUG = False

        self.NUM_REGEX = re.compile(r"\d+(\.\d+)?")
        self.HEADER_REGEX = re.compile(r"[^:]+:\s*\n")
        self.DISK_BPS_REGEX = re.compile(r"(\d+\.\d+)\s*(M|G)")


config = Config()
