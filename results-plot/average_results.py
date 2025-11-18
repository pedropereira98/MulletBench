import os
import re
import argparse
from collections import defaultdict

from numpy import average

from collections import OrderedDict
import re

MAX_DECIMAL_PLACES = 3

def parse_result(file_path):
    with open(file_path, "r") as f:
        lines = f.readlines()

    root = OrderedDict()
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
            parent[section_name] = OrderedDict()
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
def read_results(folder_path):
    results = []
    for subdir in os.listdir(folder_path):
        subdir_path = os.path.join(folder_path, subdir)
        if os.path.isdir(subdir_path):
            result_file = os.path.join(subdir_path, "results.txt")
            if os.path.isfile(result_file):
                results.append(parse_result(result_file))
    return results

import math

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

            avg = round(sum(nums) / len(nums), MAX_DECIMAL_PLACES)
            mn = round(min(nums), MAX_DECIMAL_PLACES)
            mx = round(max(nums), MAX_DECIMAL_PLACES)
            var = round(sum((x - avg) ** 2 for x in nums) / len(nums), MAX_DECIMAL_PLACES)
            std = round(math.sqrt(var), MAX_DECIMAL_PLACES)

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



import os

def save_to_file(folder_path, processed_results):
    output_file = os.path.join(folder_path, "aggregated_results.txt")
    
    repeated_key_pattern = re.compile(r"^(.*)_(\d+)$")
    
    def write_dict(d, file, indent=0):
        indent_str = " " * indent
        
        for key, value in d.items():
            
            match = repeated_key_pattern.match(key)
            if match:
                key = match.group(1)

            if isinstance(value, dict):
                file.write(f"{indent_str}{key}:\n")
                write_dict(value, file, indent + 2)
                continue

            if isinstance(value, tuple) and len(value) == 5:
                avg, unit, std, mn, mx = value
                
                unit_str = f" {unit}" if unit else ""
                file.write(
                    f"{indent_str}{key}: "
                    f"{avg}{unit_str} "
                    f"(std dev: {std}, min: {mn}, max: {mx})\n"
                )
                continue

            if isinstance(value, tuple) and isinstance(value[0], str):
                file.write(f"{indent_str}{key}: {value[0]}\n")
                continue

            if value is None:
                file.write(f"{indent_str}{key}: N/A\n")
                continue

            file.write(f"{indent_str}{key}: {value}\n")

    with open(output_file, "w") as file:
        write_dict(processed_results, file)

    print(f"Processed results saved to {output_file}")


def main():
    parser = argparse.ArgumentParser(description="Read and process benchmark results.")
    parser.add_argument("folder_path", type=str, help="Path to the test results folder")
    args = parser.parse_args()
    
    if not os.path.exists(args.folder_path):
        print("Invalid folder path.")
        return
    
    results = read_results(args.folder_path)
    processed_results = process_results(results)
    save_to_file(args.folder_path, processed_results)

if __name__ == "__main__":
    main()