from config import config
import os
import re
import math

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
        beginning = 0
        while not lines[beginning].startswith("Test results:"):
            beginning += 1
        lines = lines[beginning + 1:]
        
    root = dict()
    stack = [(-1, root)]  # (indentation level, current dict)

    key_val_pattern = re.compile(r"^\s*([^:]+):\s*(.*)$")
    val_unit_pattern = re.compile(r"^([-+]?\d*\.?\d+)\s*(\S+(\s+\S+)*)?$")

    for line in lines:
        if not line.strip():
            continue

        indent = len(line) - len(line.lstrip(" "))

        # Find parent based on indentation
        while stack and stack[-1][0] >= indent:
            stack.pop()

        parent = stack[-1][1]

        # SECTION HEADER (ends with ":" and no immediate value)
        if line.strip().endswith(":") and not re.search(r":\s*\S", line):
            section_name = line.strip().rstrip(":")
            parent[section_name] = dict()
            stack.append((indent, parent[section_name]))
            continue

        # KEY: VALUE
        match = key_val_pattern.match(line)
        if match:
            key = match.group(1).strip()
            val = match.group(2).strip()

            # Handle duplicates (Insertion rate, etc.)
            if key in parent:
                i = 1
                while f"{key}_{i}" in parent:
                    i += 1
                key = f"{key}_{i}"

            # Try to interpret as number + optional unit
            m2 = val_unit_pattern.match(val)
            if m2:
                num = float(m2.group(1))
                unit = m2.group(2)
                parent[key] = (num, unit.strip() if unit else None)
            else:
                parent[key] = (val, None)

            continue

    return root

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


def process_results(results):
    def process_node(values):
        
        if all(isinstance(v, tuple) and isinstance(v[0], (int, float)) for v in values if v is not None):
            nums = []
            unit = None

            for v in values:
                if v is None:
                    nums.append(0.0)
                else:
                    nums.append(v[0])
                    if v[1] and unit is None:
                        unit = v[1]


            avg = round(sum(nums) / len(nums), config.MAX_DECIMAL_PLACES)
            mn = round(min(nums), config.MAX_DECIMAL_PLACES)
            mx = round(max(nums), config.MAX_DECIMAL_PLACES)
            var = round(sum((x - avg) ** 2 for x in nums) / len(nums), config.MAX_DECIMAL_PLACES)
            std = round(math.sqrt(var), config.MAX_DECIMAL_PLACES)

            return (avg, unit, std, mn, mx)

        if all(isinstance(v, tuple) and isinstance(v[0], str) for v in values if v is not None):
            strings = [v[0] for v in values if v is not None]
            if len(set(strings)) == 1:
                return (strings[0], None)
            else:
                return (strings[0], None)

        if all(isinstance(v, dict) for v in values if v is not None):
            result = {}
            
            all_keys = []
            
            for v in values:
                if v and not all(k in all_keys for k in v.keys()):
                    all_keys = list(v.keys())
            
            for k in all_keys:
                subvals = [
                    v.get(k) if isinstance(v, dict) else None
                    for v in values
                ]
                result[k] = process_node(subvals)
            return result

        return next((v for v in values if v is not None), None)

    return process_node(results)

def get_results_from_run(run_name: str):
    folder = os.path.join(config.OUTPUT_PATH, run_name)

    results: list[dict] = []

    for file in os.listdir(folder):
        result = get_results_from_file(os.path.join(folder, file))
        results.append(result)

    final = process_results(results)

    return final
