from config import config
from workload import WorkloadType
from compare import compare_results
import time

def calculate_next_value_disk_io(current_io_value: dict[str, float], max_io_value: dict[str, float], reference_results: dict, adjusted_results: dict, config_type = WorkloadType.INSERTION) -> dict[str, float]:
    """ Calculate the next Disk I/O value based on the current Disk I/O value and the difference between reference and adjusted results.

    Args:
        current_io_value (dict[str, float]): Current Disk I/O values to adjust
        reference_results (dict): Results of the reference run
        adjusted_results (dict): Results of the last adjusted test run
        config_type (_type_, optional): The type of the test run. Defaults to WorkloadType.INSERTION.

    Returns:
        dict[str, float]: The next Disk I/O values to use for the next test run.
    """
    divisor = 1

    diff = compare_results(reference_results, adjusted_results, config_type)

    if abs(diff) >= 0.8:
        diff = 0.8 if diff > 0 else -0.8

    config.DISK_IO_HISTORY.append((current_io_value, diff, time.time()))
    config.DISK_IO_HISTORY.sort(key=lambda x: int(x[0]['write_iops']))

    lower = upper = None

    for i in range(1, len(config.DISK_IO_HISTORY)):
        val1, val2 = config.DISK_IO_HISTORY[i-1], config.DISK_IO_HISTORY[i]
        if val1[1] * val2[1] < 0:
            lower, upper = val1, val2

    next_io_values = {}

    if lower and upper:
        weight_low = 1 / abs(lower[1])
        weight_up = 1 / abs(upper[1])

        for k in current_io_value.keys():
            v1 = lower[0][k]
            v2 = upper[0][k]

            if "iops" in k:
                v1 = int(v1)
                v2 = int(v2)
                v = (weight_low * v1 + weight_up * v2) / (weight_low + weight_up)
                max_val_str = max_io_value.get(k)
                if max_val_str is not None:
                    v = min(v, int(max_val_str))
                next_io_values[k] = str(round(v))
            else:
                num1, unit1 = config.DISK_BPS_REGEX.match(v1).groups()
                num2, _ = config.DISK_BPS_REGEX.match(v2).groups()
                v1 = float(num1)
                v2 = float(num2)
                v = (weight_low * v1 + weight_up * v2) / (weight_low + weight_up)
                max_val_str = max_io_value.get(k)
                if max_val_str is not None:
                    num_max, _ = config.DISK_BPS_REGEX.match(max_val_str).groups()
                    v = min(v, float(num_max))
                next_io_values[k] = f"{v:.{config.MAX_DECIMAL_PLACES}f}{unit1}"
    else:

        for k, val in current_io_value.items():
            if "iops" in k:
                val = int(val)
                max_val = int(max_io_value.get(k, None))
                if max_val is not None:
                    val = min(val, max_val)
                next_io_values[k] = str(round(val - (val * diff) / divisor))
            else:
                num, unit = config.DISK_BPS_REGEX.match(val).groups()
                num = float(num)
                max_val = max_io_value.get(k, None)
                if max_val is not None:
                    num_max, _ = config.DISK_BPS_REGEX.match(max_val).groups()
                    num = min(num, float(num_max))
                next_io_values[k] = f"{round(num - (num * diff) / divisor, config.MAX_DECIMAL_PLACES)}{unit}"

    return next_io_values
