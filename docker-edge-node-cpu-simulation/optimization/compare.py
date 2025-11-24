from config import config
import state
from workload import WorkloadType

INSERT_RATE = "Insertion rate"
INSERT_LATENCY = "Average latency"
INSERT_COUNT = "Insert count"
QUERY_COUNT = "Query count"
QUERY_RATE = "Query rate_1"
QUERY_LATENCY = "Average"
FAILED_INSERT_COUNT = "Failed insert count"
FAILED_QUERY_COUNT = "Failed query count"
STATS_PER_NODE = "Stats per node"

def compare(reference_results: list[str], adjusted_results: list[str], key: str):
    """ Compare a specific metric between the reference and adjusted results.

    Args:
        reference_results (list[str]): Results of the reference run
        adjusted_results (list[str]): Results of the last adjusted test run
        key (str): The specific metric to compare

    Returns:
        float: The difference between the adjusted and reference results for the specified metric.
    """
    return (adjusted_results[key][0] / reference_results[key][0]) - 1


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

    ref_edge_stats = reference_results[STATS_PER_NODE][f"{state.edge_node_name} stats"]
    adj_edge_stats = adjusted_results[STATS_PER_NODE][f"{state.edge_node_name} stats"]

    if config_type == WorkloadType.INSERTION: # INSERTION workload
        failed_insert_ref = ref_edge_stats.get(FAILED_INSERT_COUNT, 0.0)[0]
        failed_insert_adj = adj_edge_stats.get(FAILED_INSERT_COUNT, 0.0)[0]

        total_insertions = failed_insert_ref + ref_edge_stats[INSERT_COUNT][0]
        margin = total_insertions * config.FAILED_OPERATION_MARGIN
        failed_insert_diff = failed_insert_ref - failed_insert_adj
        
        if not -margin < failed_insert_diff < margin: # Failed operations difference is too high
            diff = failed_insert_diff / total_insertions
        else:
            diff_rate = compare(ref_edge_stats, adj_edge_stats, INSERT_RATE)
            diff_latency = compare(adj_edge_stats, ref_edge_stats, INSERT_LATENCY)

            diff = (diff_rate + diff_latency) * 0.5
    elif config_type == WorkloadType.QUERY: # QUERY workload
        ref_edge_stats = ref_edge_stats["Query stats"]
        adj_edge_stats = adj_edge_stats["Query stats"]

        failed_query_ref = ref_edge_stats.get(FAILED_QUERY_COUNT, 0.0)[0]
        failed_query_adj = adj_edge_stats.get(FAILED_QUERY_COUNT, 0.0)[0]

        total_queries = failed_query_ref + ref_edge_stats[QUERY_COUNT][0]
        margin = total_queries * config.FAILED_OPERATION_MARGIN
        failed_query_diff = failed_query_ref - failed_query_adj

        if not -margin < failed_query_diff < margin:
            diff = failed_query_diff / total_queries
        else:
            diff_rate = compare(ref_edge_stats, adj_edge_stats, QUERY_RATE)
            diff_latency = compare(adj_edge_stats, ref_edge_stats, QUERY_LATENCY)
            diff = (diff_rate + diff_latency) * 0.5
            # diff = diff_rate
    else: # MIXED workload
        ref_query_stats = ref_edge_stats["Query stats"]
        adj_query_stats = adj_edge_stats["Query stats"]
        
        diff_failed_insert, diff_failed_query = None, None

        failed_insert_ref = ref_edge_stats.get(FAILED_INSERT_COUNT, 0.0)[0]
        failed_insert_adj = adj_edge_stats.get(FAILED_INSERT_COUNT, 0.0)[0]

        total_insertions = failed_insert_ref + ref_edge_stats[INSERT_COUNT][0]
        margin_insert = total_insertions * config.FAILED_OPERATION_MARGIN
        failed_insert_diff = failed_insert_ref - failed_insert_adj

        if not -margin_insert < failed_insert_diff < margin_insert:
            diff_failed_insert = failed_insert_diff / total_insertions

        failed_query_ref = ref_query_stats.get(FAILED_QUERY_COUNT, 0.0)[0]
        failed_query_adj = adj_query_stats.get(FAILED_QUERY_COUNT, 0.0)[0]

        total_queries = failed_query_ref + ref_query_stats[QUERY_COUNT][0]
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
            diff_insert_rate = compare(ref_edge_stats, adj_edge_stats, INSERT_RATE)
            diff_insert_latency = compare(adj_edge_stats, ref_edge_stats, INSERT_LATENCY)
            diff_query_rate = compare(ref_query_stats, adj_query_stats, QUERY_RATE)
            diff_query_latency = compare(adj_query_stats, ref_query_stats, QUERY_LATENCY)
            
            diff_insert = (diff_insert_rate + diff_insert_latency) / 2
            # diff_query = (diff_query_rate + diff_query_latency) / 2
            diff_query = diff_query_latency

            diff = (diff_insert + diff_query) / 2

    return round(diff, config.MAX_DECIMAL_PLACES)