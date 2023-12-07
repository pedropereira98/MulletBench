package pt.haslab.mulletbench.stats;

import pt.haslab.mulletbench.OperationType;

import java.io.Serializable;

public record StatsEntry (long before, long after, int count, OperationType type) implements Serializable{
    // Below methods are only called after workload execution

    public long getWriteTime() {
        return after - before;
    }

    public int getCount() {
        return count;
    }

    public long getLatency(){
        return after-before;
    }

    public String toCSV(){
        return before + "," + after + "," + count + "," + type;
    }

    public static void main(String[] args) {
        StatsEntry se = new StatsEntry(System.nanoTime()-1_000_000, System.nanoTime(), 100, OperationType.INSERT);

        System.out.println(se.toCSV());
    }
}