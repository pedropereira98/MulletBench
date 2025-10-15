from config import config
from workload import WorkloadType

def compare(reference_results: list[str], adjusted_results: list[str], key: str):
    """ Compare a specific metric between the reference and adjusted results.

    Args:
        reference_results (list[str]): Results of the reference run
        adjusted_results (list[str]): Results of the last adjusted test run
        key (str): The specific metric to compare

    Returns:
        float: The difference between the adjusted and reference results for the specified metric.
    """
    return (adjusted_results[key] / reference_results[key]) - 1


def compare_results(reference_results: list[str], adjusted_results: list[str], config_type = WorkloadType.INSERTION) -> float:
    """ Compare the adjusted results with the reference results to determine the difference in performance.

    Args:
        reference_results (list[str]): Results of the reference run
        adjusted_results (list[str]): Results of the last adjusted test run
        config_type (_type_, optional): The type of the test run. Defaults to WorkloadType.INSERTION.

    Returns:
        float: The difference between the adjusted and reference results.
    """

    diff = 0

    if config_type == WorkloadType.INSERTION: # INSERTION workload
        failed_insert_ref = reference_results.get(config.FAILED_INSERT_COUNT, 0.0)
        failed_insert_adj = adjusted_results.get(config.FAILED_INSERT_COUNT, 0.0)

        total_insertions = failed_insert_ref + reference_results[config.INSERT_COUNT]
        margin = total_insertions * config.FAILED_OPERATION_MARGIN
        failed_insert_diff = failed_insert_ref - failed_insert_adj
        
        if not -margin < failed_insert_diff < margin: # Failed operations difference is too high
            diff = failed_insert_diff / total_insertions
        else:
            diff_rate = compare(reference_results, adjusted_results, config.INSERT_RATE)
            diff_latency = compare(adjusted_results, reference_results, config.INSERT_LATENCY)

            diff = (diff_rate + diff_latency) * 0.5
    elif config_type == WorkloadType.QUERY: # QUERY workload
        failed_query_ref = reference_results.get(config.FAILED_QUERY_COUNT, 0.0)
        failed_query_adj = adjusted_results.get(config.FAILED_QUERY_COUNT, 0.0)

        total_queries = failed_query_ref + reference_results[config.QUERY_COUNT]
        margin = total_queries * config.FAILED_OPERATION_MARGIN
        failed_query_diff = failed_query_ref - failed_query_adj

        if not -margin < failed_query_diff < margin:
            diff = failed_query_diff / total_queries
        else:
            diff_rate = compare(reference_results, adjusted_results, config.QUERY_RATE)
            diff_latency = compare(adjusted_results, reference_results, config.QUERY_LATENCY)
            diff = (diff_rate + diff_latency) * 0.5
            # diff = diff_rate
    else: # MIXED workload
        diff_failed_insert, diff_failed_query = None, None

        failed_insert_ref = reference_results.get(config.FAILED_INSERT_COUNT, 0.0)
        failed_insert_adj = adjusted_results.get(config.FAILED_INSERT_COUNT, 0.0)

        total_insertions = failed_insert_ref + reference_results[config.INSERT_COUNT]
        margin_insert = total_insertions * config.FAILED_OPERATION_MARGIN
        failed_insert_diff = failed_insert_ref - failed_insert_adj

        if not -margin_insert < failed_insert_diff < margin_insert:
            diff_failed_insert = failed_insert_diff / total_insertions

        failed_query_ref = reference_results.get(config.FAILED_QUERY_COUNT, 0.0)
        failed_query_adj = adjusted_results.get(config.FAILED_QUERY_COUNT, 0.0)

        total_queries = failed_query_ref + reference_results[config.QUERY_COUNT]
        margin_query = total_queries * config.FAILED_OPERATION_MARGIN
        failed_query_diff = failed_query_ref - failed_query_adj

        if not -margin_query < failed_query_diff < margin_query:
            diff_failed_query = failed_query_diff / total_queries

        if diff_failed_query is not None and diff_failed_insert is not None:
            diff = (diff_failed_insert + diff_failed_query) / 2
        elif diff_failed_query is not None:
            diff = diff_failed_query
        elif diff_failed_insert is not None:
            diff = diff_failed_insert
        else:
            metrics_rate = [config.INSERT_RATE, config.QUERY_RATE]
            metrics_latency = [config.INSERT_LATENCY, config.QUERY_LATENCY]

            diff_rate = sum([compare(reference_results, adjusted_results, metric) for metric in metrics_rate]) / len(metrics_rate)
            diff_latency = sum([compare(adjusted_results, reference_results, metric) for metric in metrics_latency]) / len(metrics_latency)
            diff = (diff_rate + diff_latency) / 2
            # diff_insert = compare(reference_results, adjusted_results, INSERT_RATE) + compare(adjusted_results, reference_results, INSERT_LATENCY)
            # diff_query = compare(reference_results, adjusted_results, QUERY_RATE) + compare(adjusted_results, reference_results, QUERY_LATENCY)
            # diff = (diff_insert + diff_query) / 4

    return round(diff, config.MAX_DECIMAL_PLACES)