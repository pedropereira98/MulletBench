package pt.haslab.mulletbench.stats;

import pt.haslab.mulletbench.OperationType;

import java.util.List;
import java.util.stream.Collectors;

public class QueryStats extends Stats {
    
    public QueryStats() {
        super();
    }

    private QueryStats(List<StatsEntry> operations){
        super(operations);
    }

    @Override
    protected int getCount() {
        return (int) this.operations.stream().filter(statsEntry -> statsEntry.type().isValidQuery()).count();
    }

    @Override
    protected int getVolume() {
        return operations.stream().filter(statsEntry -> statsEntry.type().isValidQuery()).mapToInt(StatsEntry::getCount).sum();
    }

    private void printLatencyStats(){
        System.out.println("Queried volume: " + this.getVolume());
        System.out.println("Query count: " + this.getCount());
        int failedQueryCount = this.getFailedQueryCount();
        if(failedQueryCount > 0){
            System.out.println("Failed query count: " + failedQueryCount);
        }

        System.out.println("Latency breakdown:");
        System.out.println("Minimum: " + this.getMinimum()/1_000_000L + "ms");
        System.out.println("10th percentile: " + this.getPercentile(10)/1_000_000L + "ms");
        System.out.println("Average: " + this.getAverage()/1_000_000L + "ms");
        System.out.println("90th percentile: " + this.getPercentile(90)/1_000_000L + "ms");
        System.out.println("Maximum: " + this.getMaximum()/1_000_000L + "ms");
        System.out.println("Median: " + this.getMedian()/1_000_000L + "ms");
        System.out.println("Standard deviation: " + this.getStandardDeviation()/1_000_000L + "ms");
    }

    public void printStats(float globalTimeS){
        System.out.println();
        System.out.println("Total time: " + globalTimeS + " seconds");
        System.out.println("Query rate: " + (float) this.getVolume() / (globalTimeS) + " query records/s");
        System.out.println("Query rate: " + (float) this.getCount() / (globalTimeS) + " queries ops/s");

        printLatencyStats();

        QueryStats outlierFilterStats = QueryStats.from(operations.stream().filter(op -> op.type().equals(OperationType.OUTLIER_FILTER)).collect(Collectors.toList()));
        QueryStats filterStats = QueryStats.from(operations.stream().filter(op -> op.type().equals(OperationType.FILTER)).collect(Collectors.toList()));
        QueryStats downsamplingStats = QueryStats.from(operations.stream().filter(op -> op.type().equals(OperationType.DOWNSAMPLING)).collect(Collectors.toList()));
        QueryStats aggregationStats = QueryStats.from(operations.stream().filter(op -> op.type().equals(OperationType.AGGREGATION)).collect(Collectors.toList()));

        if(!outlierFilterStats.operations.isEmpty()){
            System.out.println("\nOutlier filter queries:");
            long failedOutlierFilters = this.operations.stream().filter(statsEntry -> statsEntry.type().equals(OperationType.FAILED_OUTLIER_FILTER)).count();
            if(failedOutlierFilters > 0){
                System.out.println("Failed: " + failedOutlierFilters);
            }
            outlierFilterStats.printLatencyStats();
        }

        if(!filterStats.operations.isEmpty()){
            System.out.println("\nFilter queries:");
            long failedFilters = this.operations.stream().filter(statsEntry -> statsEntry.type().equals(OperationType.FAILED_FILTER)).count();
            if(failedFilters > 0){
                System.out.println("Failed: " + failedFilters);
            }
            filterStats.printLatencyStats();

        }

        if(!aggregationStats.operations.isEmpty()) {
            System.out.println("\nAggregation queries:");
            long failedAggregations = this.operations.stream().filter(statsEntry -> statsEntry.type().equals(OperationType.FAILED_AGGREGATION)).count();
            if(failedAggregations > 0){
                System.out.println("Failed: " + failedAggregations);
            }
            aggregationStats.printLatencyStats();
        }

        if(!downsamplingStats.operations.isEmpty()) {
            System.out.println("\nDownsampling queries:");
            long failedDownscaling = this.operations.stream().filter(statsEntry -> statsEntry.type().equals(OperationType.FAILED_DOWNSAMPLING)).count();
            if(failedDownscaling > 0){
                System.out.println("Failed: " + failedDownscaling);
            }
            downsamplingStats.printLatencyStats();
        }
    }

    private int getFailedQueryCount() {
        return (int) this.operations.stream().filter(statsEntry -> statsEntry.type().isFailedQuery()).count();
    }

    static protected QueryStats from(List<StatsEntry> operations){
        return new QueryStats(operations);
    }
}
