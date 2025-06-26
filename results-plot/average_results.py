import os
import re
import argparse
from collections import defaultdict

from numpy import average

def read_results(folder_path):
    results = []
    for subdir in os.listdir(folder_path):
        subdir_path = os.path.join(folder_path, subdir)
        if os.path.isdir(subdir_path):
            result_file = os.path.join(subdir_path, "results.txt")
            if os.path.isfile(result_file):
                with open(result_file, "r") as file:
                    results.append(file.readlines())
    return results

def process_results(results):
    processed_lines = []
    
    for line1, line2, line3 in zip(*results):
        match1 = re.match(r"(.*?):\s+([\d\.]+)\s*((?:\w+ )*\w+)?", line1.strip())
        match2 = re.match(r"(.*?):\s+([\d\.]+)\s*((?:\w+ )*\w+)?", line2.strip())
        match3 = re.match(r"(.*?):\s+([\d\.]+)\s*((?:\w+ )*\w+)?", line3.strip())
        if match1 and match2 and match3:
            key1, value1, unit1 = match1.groups()
            _, value2, _ = match2.groups()
            _, value3, _ = match3.groups()
            
            average_value = average([float(value1), float(value2), float(value3)])
            std_dev = average([(float(value1) - average_value) ** 2, (float(value2) - average_value) ** 2, (float(value3) - average_value) ** 2]) ** 0.5
            min_value = min(float(value1), float(value2), float(value3))
            max_value = max(float(value1), float(value2), float(value3))
            processed_lines.append(f"{key1}: {average_value:.2f} {unit1 if unit1 is not None else ''} (std dev: {std_dev:.2f}, min: {min_value:.2f}, max: {max_value:.2f})")
        else:
            processed_lines.append(line1.strip())
    
    return processed_lines

def save_to_file(folder_path, processed_results):
    output_file = os.path.join(folder_path, "aggregated_results.txt")
    with open(output_file, "w") as file:
        for line in processed_results:
            file.write(f"{line}\n")
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