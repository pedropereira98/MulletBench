from config import config

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
    
    results = {}
    level = 0

    cloud_line_flag = False
    global_flag = False

    last_header = ""
    for line in lines:
        if cloud_line_flag:
            if global_flag :

                if len(line) <= 1:
                    level = 0
                    continue

                if level > 0:
                    continue
                
                if config.HEADER_REGEX.match(line):
                    header = line.split(":")[0]
                    if header != "Latency breakdown":
                        level += 1
                    continue

                key, raw_value = line.split(": ")

                if "ops/s" in raw_value:
                    key += " ops"

                match = config.NUM_REGEX.match(raw_value)
                value = float(match.group(0))

                key_orig = key
                i = 1
                while key in results.keys():
                    key = f"{key_orig}_{i}"
                    i += 1

                results[key] = value

            elif "Global stats:" in line:
                global_flag = True
        elif "Cloud database node stats:" in line:
            cloud_line_flag = True
    
    return results 

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
