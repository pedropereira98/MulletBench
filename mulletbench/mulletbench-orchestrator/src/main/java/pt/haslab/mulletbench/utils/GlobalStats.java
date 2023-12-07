package pt.haslab.mulletbench.utils;


import pt.haslab.mulletbench.stats.InsertionStats;
import pt.haslab.mulletbench.stats.QueryStats;
import pt.haslab.mulletbench.stats.StatsCollector;

public class GlobalStats {
    private final QueryStats queryStats;
    private final InsertionStats insertStats;

    long queryStart;
    long queryEnd;

    long insertStart;
    long insertEnd;

    public GlobalStats(){
        this.queryStats = new QueryStats();
        this.insertStats = new InsertionStats();

        this.queryStart = Long.MAX_VALUE;
        this.queryEnd = Long.MIN_VALUE;

        this.insertStart = Long.MAX_VALUE;
        this.insertEnd = Long.MIN_VALUE;
    }

    public void joinCollector(StatsCollector collector){
        switch(collector.type){
            case QUERY:
                collector.stats.forEach(queryStats::join);
                queryStart = Math.min(queryStart, collector.start);
                queryEnd = Math.max(queryEnd, collector.end);
                break;
            case INSERT:
                collector.stats.forEach(insertStats::join);
                insertStart = Math.min(insertStart, collector.start);
                insertEnd = Math.max(insertEnd, collector.end);
                break;
        }
    }

    public void printStats() {
        if(queryStart != Long.MAX_VALUE){
            queryStats.printStats((float) (queryEnd - queryStart) / 1_000_000_000L);
        }
        if(insertStart != Long.MAX_VALUE){
            insertStats.printStats((float) (insertEnd - insertStart) / 1_000_000_000L);
        }
    }
}
