package pt.haslab.mulletbench.stats;

import pt.haslab.mulletbench.OperationType;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public abstract class Stats implements Serializable {

    protected final List<StatsEntry> operations;

    protected Stats() {
        this.operations = new LinkedList<>();
    }

    protected Stats(List<StatsEntry> operations){
        this.operations = new LinkedList<>(operations);
    }

    public void registerOperation(long before, long after, int size, OperationType type){
        StatsEntry e = new StatsEntry(before, after, size, type);
        synchronized(operations){
            operations.add(e);
        }
    }

    // Below methods are only called after workload execution

    protected long getTime() {
        return operations.stream().mapToLong(StatsEntry::getWriteTime).sum();
    }

    protected int getCount() {
        return operations.size();
    }

    private LongStream toLatencyStream(){
        return operations.stream().mapToLong(StatsEntry::getLatency);
    }

    protected int getVolume() {
        return operations.stream().mapToInt(StatsEntry::getCount).sum();
    }

    protected long getMinimum(){
        return toLatencyStream().min().orElse(Long.MAX_VALUE); // if error return high latency value
    }

    protected long getMaximum(){
        return toLatencyStream().max().orElse(Long.MAX_VALUE);
    }

    protected double getAverage(){
        return toLatencyStream().average().orElse(Long.MAX_VALUE);
    }

    protected double getMedian(){
        LongStream sortedLatencies = toLatencyStream().sorted();
        return this.operations.size() % 2 == 0 ?
            sortedLatencies.skip(this.operations.size()/2-1).limit(2).average().getAsDouble():        
            sortedLatencies.skip(this.operations.size()/2).findFirst().getAsLong();
    }

    protected long getPercentile(double percentile){
        int index = (int) Math.ceil(percentile / 100.0 * this.operations.size());
        return toLatencyStream().sorted().skip(index).findFirst().orElse(Long.MAX_VALUE);
    }

    protected double getStandardDeviation(){
        long[] array = toLatencyStream().toArray();
        
        // get the sum of array
        double sum = 0.0;
        for (double i : array) {
            sum += i;
        }

        // get the mean of array
        int length = array.length;
        double mean = sum / length;
    
        // calculate the standard deviation
        double standardDeviation = 0.0;
        for (double num : array) {
            standardDeviation += Math.pow(num - mean, 2);
        }
    
        return Math.sqrt(standardDeviation / length);
    }

    protected abstract void printStats(float globalTimeS);

    public List<String> toCSV(){
        return operations.stream().map(StatsEntry::toCSV).collect(Collectors.toList());
    }

    public void join(Stats toJoin){
        this.operations.addAll(toJoin.operations);
    }
}
