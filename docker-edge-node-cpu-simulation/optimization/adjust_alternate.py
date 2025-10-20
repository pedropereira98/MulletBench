from config import config
from workload import WorkloadType
from optimization.compare import compare_results
import time
import math

ALTERNATE_HISTORY: list[tuple[float, int, float, float]] = [] # (axis,bias,diff,timestamp)
AXIS_HISTORY: list[tuple[float, int, float]] = [] # (axis,bias,diff)
KEEP_AXIS = False

def calculate_axis(cpu_value: float, current_disk_multiplier: float):
    return round(cpu_value * current_disk_multiplier, config.MAX_DECIMAL_PLACES)

def axis_to_values(axis_value: float, bias: int) -> tuple[float, float]:
    """ Calculate the value for cpu and disk multiplier from axis value

    Args:
        axis_value (float): value of the axis to reverse
        float (int): whether to bias return for cpu, disk, or no bias (<0, >0 or 0, respectivelly)
        
    Returns:
        tuple[float, float]: values for cpu and disk multiplier
    """
    
    value = math.sqrt(axis_value)
    
    value_cpu = round(value * (1 - bias * 0.05), config.MAX_DECIMAL_PLACES)
    value_disk = round(value * (1 + bias * 0.05), config.MAX_DECIMAL_PLACES)
    
    return (value_cpu, value_disk) 

def solve_axis(axis_value: float, other_value: float):
    return axis_value / other_value

def calculate_next_value_alternate(
        current_cpu_value: float, 
        current_io_multiplier: dict[str, float],
        current_bias: int,
        adjust_cpu: bool,
        reference_results: dict,
        adjusted_results: dict,
        config_type = WorkloadType.INSERTION) -> tuple[float, dict[str, float], int]:
    """ Calculate the next CPU and Disk I/O values alternately based on the current values and the difference between reference and adjusted results.

    Args:
        current_cpu_value (float): Current CPU value to adjust
        current_io_multiplier (float): Current Disk I/O multiplier
        adjust_cpu (bool): Boolean indicating whether CPU should be adjusted instead of Disk I/O 
        reference_results (dict): Results of the reference run
        adjusted_results (dict): Results of the last adjusted test run
        config_type (_type_, optional): The type of the test run. Defaults to WorkloadType.INSERTION.

    Returns:
        tuple[float, dict[str, float], float]: The next CPU and Disk I/O values to use for the next test run, along with the current bias for axis reverse caclulation
    """
    global ALTERNATE_HISTORY, KEEP_AXIS

    divisor = 1

    diff = min(max(compare_results(reference_results, adjusted_results, config_type), -0.8), 0.8)

    axis_value = calculate_axis(current_cpu_value, current_io_multiplier)
    
    if KEEP_AXIS:
        AXIS_HISTORY.sort(key=lambda x: x[1])
        
        if AXIS_HISTORY[-1][1] <= 0: ## hasn't explored positive bias
            bias = 1
            (cpu, disk) = axis_to_values(axis_value, bias)
            
            return (cpu, disk, bias)
        
        bias_used = list(map(lambda x: x[1], AXIS_HISTORY)).sort()
        
        best_result = min(AXIS_HISTORY, key=lambda x: x[2])

        # if best_result is either the lowest or highest bias, keep going in that direction
        if best_result[1] == min(bias_used):
            bias = min(bias_used) - 1
            
            (cpu, disk) = axis_to_values(axis_value, bias)
            return (cpu, disk, bias)
        elif best_result[1] == max(bias_used):
            bias = max(bias_used) + 1
            
            (cpu, disk) = axis_to_values(axis_value, bias)
            return (cpu, disk, bias)
        else:
            _, bias, diff = best_result
            
            (cpu, disk) = axis_to_values(axis_value, bias)
            
            current_cpu_value = cpu
            current_io_multiplier = disk
            current_bias = bias
            
            KEEP_AXIS = False
            AXIS_HISTORY.clear()
    elif diff <= 0.15:
        KEEP_AXIS = True
        AXIS_HISTORY.append(axis_value, 0, diff)
        
        bias = -1 # prioritize higher cpu and lower disk
        
        cpu, disk = axis_to_values(axis_value, bias)
        
        return  (cpu, disk, bias)

    
    ALTERNATE_HISTORY.append((axis_value, current_bias, diff, time.time()))
    ALTERNATE_HISTORY.sort(key= lambda x: x[0])
    
    lower = upper = None

    for i in range(1, len(ALTERNATE_HISTORY)):
        val1 ,val2 = ALTERNATE_HISTORY[i-1], ALTERNATE_HISTORY[i]
        
        if val1[2] * val2[2] < 0:
            lower, upper = val1, val2
            break
        
    if lower and upper:
        weight_low = 1 / abs(lower[2])
        weight_up = 1 / abs(upper[2])
        next_axis = (lower[0] * weight_low + upper[0] * weight_up) / (weight_low + weight_up)
        next_bias = round((lower[1] * weight_low + upper[1] * weight_up) / (weight_low + weight_up))
        
        cpu, disk = axis_to_values(next_axis, next_bias)
        return (cpu, disk, next_bias)
        
    next_axis = axis_value - (diff * axis_value)
    
    if current_io_multiplier == 1:
        return (solve_axis(next_axis, 1.0), 1.0, 0)
    
    next_cpu = current_cpu_value
    next_disk = current_io_multiplier
    
    if adjust_cpu:
        next_cpu = solve_axis(next_axis, current_io_multiplier)
    else:
        disk = solve_axis(next_axis, current_io_multiplier)
        
        if disk > 1:
            next_disk = 1
            next_cpu = solve_axis(axis_value, 1.0)
        else:
            next_disk = disk

    return (next_cpu, next_disk, 0)