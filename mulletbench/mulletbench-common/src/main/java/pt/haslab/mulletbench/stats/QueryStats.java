package pt.haslab.mulletbench.stats;

import java.util.List;
import java.util.stream.Collectors;

import pt.haslab.mulletbench.IndentString;
import pt.haslab.mulletbench.OperationType;

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

    private void printLatencyStats(int indentation){
        System.out.println(IndentString.indent(indentation) + "Queried volume: " + this.getVolume());
        System.out.println(IndentString.indent(indentation) + "Query count: " + this.getCount());
        int failedQueryCount = this.getFailedQueryCount();
        if(failedQueryCount > 0){
            System.out.println(IndentString.indent(indentation) + "Failed query count: " + failedQueryCount);
        }

        System.out.println(IndentString.indent(indentation) + "Latency breakdown:");
        indentation++;
        System.out.println(IndentString.indent(indentation) + "Minimum: " + this.getMinimum()/1_000_000L + "ms");
        System.out.println(IndentString.indent(indentation) + "10th percentile: " + this.getPercentile(10)/1_000_000L + "ms");
        System.out.println(IndentString.indent(indentation) + "Average: " + this.getAverage()/1_000_000L + "ms");
        System.out.println(IndentString.indent(indentation) + "90th percentile: " + this.getPercentile(90)/1_000_000L + "ms");
        System.out.println(IndentString.indent(indentation) + "Maximum: " + this.getMaximum()/1_000_000L + "ms");
        System.out.println(IndentString.indent(indentation) + "Median: " + this.getMedian()/1_000_000L + "ms");
        System.out.println(IndentString.indent(indentation) + "Standard deviation: " + this.getStandardDeviation()/1_000_000L + "ms");
    }

    public void printStats(float globalTimeS, int indentation){
        System.out.println(IndentString.indent(indentation) + "Query stats:");
        indentation++;
        System.out.println(IndentString.indent(indentation) + "Total time: " + globalTimeS + " seconds");
        System.out.println(IndentString.indent(indentation) + "Query rate: " + (float) this.getVolume() / (globalTimeS) + " query records/s");
        System.out.println(IndentString.indent(indentation) + "Query rate: " + (float) this.getCount() / (globalTimeS) + " queries ops/s");

        printLatencyStats(indentation);

        QueryStats outlierFilterStats = QueryStats.from(operations.stream().filter(op -> op.type().equals(OperationType.OUTLIER_FILTER)).collect(Collectors.toList()));
        QueryStats filterStats = QueryStats.from(operations.stream().filter(op -> op.type().equals(OperationType.FILTER)).collect(Collectors.toList()));
        QueryStats downsamplingStats = QueryStats.from(operations.stream().filter(op -> op.type().equals(OperationType.DOWNSAMPLING)).collect(Collectors.toList()));
        QueryStats aggregationStats = QueryStats.from(operations.stream().filter(op -> op.type().equals(OperationType.AGGREGATION)).collect(Collectors.toList()));

        if(!outlierFilterStats.operations.isEmpty()){
            System.out.println("\nOutlier filter queries:");
            indentation++;
            long failedOutlierFilters = this.operations.stream().filter(statsEntry -> statsEntry.type().equals(OperationType.FAILED_OUTLIER_FILTER)).count();
            if(failedOutlierFilters > 0){
                System.out.println(IndentString.indent(indentation) + "Failed: " + failedOutlierFilters);
            }
            outlierFilterStats.printLatencyStats(indentation);
        }

        if(!filterStats.operations.isEmpty()){
            System.out.println("\nFilter queries:");
            indentation++;
            long failedFilters = this.operations.stream().filter(statsEntry -> statsEntry.type().equals(OperationType.FAILED_FILTER)).count();
            if(failedFilters > 0){
                System.out.println(IndentString.indent(indentation) + "Failed: " + failedFilters);
            }
            filterStats.printLatencyStats(indentation);

        }

        if(!aggregationStats.operations.isEmpty()) {
            System.out.println("\nAggregation queries:");
            indentation++;
            long failedAggregations = this.operations.stream().filter(statsEntry -> statsEntry.type().equals(OperationType.FAILED_AGGREGATION)).count();
            if(failedAggregations > 0){
                System.out.println(IndentString.indent(indentation) + "Failed: " + failedAggregations);
            }
            aggregationStats.printLatencyStats(indentation);
        }

        if(!downsamplingStats.operations.isEmpty()) {
            System.out.println("\nDownsampling queries:");
            indentation++;
            long failedDownscaling = this.operations.stream().filter(statsEntry -> statsEntry.type().equals(OperationType.FAILED_DOWNSAMPLING)).count();
            if(failedDownscaling > 0){
                System.out.println(IndentString.indent(indentation) + "Failed: " + failedDownscaling);
            }
            downsamplingStats.printLatencyStats(indentation);
        }
    }

    private int getFailedQueryCount() {
        return (int) this.operations.stream().filter(statsEntry -> statsEntry.type().isFailedQuery()).count();
    }

    static protected QueryStats from(List<StatsEntry> operations){
        return new QueryStats(operations);
    }
}
