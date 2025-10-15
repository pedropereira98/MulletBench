from config import config
from workload import WorkloadType
from optimization.compare import compare_results
import time

def calculate_next_value_cpu(current_cpu_value: float, reference_results: dict, adjusted_results: dict, config_type = WorkloadType.INSERTION) -> float:
    """ Calculate the next CPU value based on the current CPU value and the difference between reference and adjusted results.

    Args:
        current_cpu_value (float): Current CPU value to adjust
        reference_results (dict): Results of the reference run
        adjusted_results (dict): Results of the last adjusted test run
        config_type (_type_, optional): The type of the test run. Defaults to WorkloadType.INSERTION.

    Returns:
        float: The next CPU value to use for the next test run.
    """

    divisor = 1

    diff = compare_results(reference_results, adjusted_results, config_type)

    if abs(diff) >= 0.8:
        diff = 0.8 if diff > 0 else -0.8

    config.CPU_HISTORY.append((current_cpu_value, diff, time.time()))
    config.CPU_HISTORY.sort(key=lambda x: x[0])

    lower = upper = None

    for i in range(1, len(config.CPU_HISTORY)):
        val1, val2 = config.CPU_HISTORY[i-1], config.CPU_HISTORY[i]
        if val1[1] * val2[1] < 0:
            lower, upper = val1, val2
            break

    if lower and upper:
        weight_low = 1 / abs(lower[1])
        weight_up = 1 / abs(upper[1])
        next_cpu = (lower[0] * weight_low + upper[0] * weight_up) / (weight_low + weight_up)
    else:
        next_cpu = current_cpu_value - (diff * current_cpu_value) / divisor

    next_cpu = max(current_cpu_value - config.MAX_ADJUST_CPU, min(current_cpu_value + config.MAX_ADJUST_CPU, next_cpu))

    return max(config.MIN_CPU_VALUE, round(next_cpu, config.MAX_DECIMAL_PLACES))
