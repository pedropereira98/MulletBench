from config import config
from state import state
from workload import WorkloadType
from optimization.compare import compare_results
import time

def multiply_disk_io(base_io_limits: dict, multiplier: float) -> dict:
    
    new_limits: dict = {}
    
    for key, raw_val in base_io_limits.items():
        
        if "iops" in key:
            val = int(raw_val)
            new_limits[key] = str(round(val * multiplier))
        else:
            num, unit = config.DISK_BPS_REGEX.match(val).groups()
            num = float(num)
            new_limits[key] = f"{round(num * multiplier, config.MAX_DECIMAL_PLACES)}{unit}"

    return new_limits


def calculate_next_value_disk_io(current_io_multiplier: float, reference_results: dict, adjusted_results: dict, config_type = WorkloadType.INSERTION) -> float:
    """ Calculate the next Disk I/O value based on the current Disk I/O value and the difference between reference and adjusted results.

    Args:
        current_io_value (dict[str, float]): Current Disk I/O values to adjust
        max_io_value (dict[str, float]): Maximum Disk I/O values
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

    state.disk_io_history.append((current_io_multiplier, diff, time.time()))
    state.disk_io_history.sort(key=lambda x: int(x[0]))

    lower = upper = None

    for i in range(1, len(state.disk_io_history)):
        val1, val2 = state.disk_io_history[i-1], state.disk_io_history[i]
        if val1[1] * val2[1] < 0:
            lower, upper = val1, val2

    next_io_multiplier = None

    if lower and upper:
        weight_low = 1 / abs(lower[1])
        weight_up = 1 / abs(upper[1])

        next_io_multiplier = (lower[0] * weight_low + upper[0] * weight_up) / (weight_low + weight_up)
    else:
        next_io_multiplier = current_io_multiplier - (diff * current_io_multiplier) / divisor

    return max(min(1.0, next_io_multiplier), 0.1)
