package pt.haslab.mulletbench.stats;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import pt.haslab.mulletbench.TimeProvider;
import pt.haslab.mulletbench.WorkloadType;

//The statsCollector as a whole is only accessed either before or after workload execution
public class StatsCollector implements Serializable {
    //List of insertion stats for each worker
    public final List<Stats> stats;
    public final WorkloadType type;

    public long start;
    public long end;

    public StatsCollector(WorkloadType type, int numWorkers) {
        this.stats = new ArrayList<>(numWorkers);
        this.type = type;
        Class<? extends Stats> statsClass = null;
        switch(type){
            case INSERT:
                statsClass = InsertionStats.class;
                break;
            case QUERY:
                statsClass = QueryStats.class;
                break;
        }
        try{
            for(int i = 0; i < numWorkers; i++){
                stats.add(statsClass.getConstructor().newInstance());
            }
        } catch (Exception e){
            System.err.println("Failed to create stats. Probably no public constructor for " + statsClass.getSimpleName() + e);
        }
    }

    public void startCollection(){
        start = TimeProvider.getNanoTime();
    }

    public void endCollection(){
        end = TimeProvider.getNanoTime();
    }

    //Below methods are only called after workload execution

    public Stats getStats(int i){
        return stats.get(i);
    }

    private void printInsertionStats(){
        InsertionStats globalStats = new InsertionStats();

        stats.forEach(globalStats::join);

        if(globalStats.getCount() > 0){
            final long globalTimeMS = (end - start)/ 1_000_000L;
            final long writeTimeMS =  stats.stream().mapToLong(Stats::getTime).sum()/ 1_000_000L;
            final float globalTimeS = (float) globalTimeMS / 1_000L;
    
            System.out.println();
            System.out.println("Global stats:");
            System.out.println("Total global time: " + globalTimeMS + "ms");
            System.out.println("Total write time: " + writeTimeMS + "ms");
            
            globalStats.printStats(globalTimeS);
        }
    }

    private void printQueryStats(){
        QueryStats globalStats = new QueryStats();

        stats.forEach(globalStats::join);

        if(globalStats.getCount() > 0){
            final long globalTimeMS = (end - start)/ 1_000_000L;
            final long queryTimeMS =  stats.stream().mapToLong(Stats::getTime).sum()/ 1_000_000L;
            final float globalTimeS = (float) globalTimeMS / 1_000L;
    
            System.out.println();
            System.out.println("Global stats:");
            System.out.println("Total global time: " + globalTimeMS + "ms");
            System.out.println("Total query time: " + queryTimeMS + "ms");
            
            globalStats.printStats(globalTimeS);
        }
    }

    public List<String> toCSV(){
        return stats.stream().map(Stats::toCSV).flatMap(List::stream).collect(Collectors.toList());
    }

    public void printStats(){
        switch(type){
            case INSERT:
                printInsertionStats();
                break;
            case QUERY:
                printQueryStats();
                break;
        }

        if(stats.size() <= 4){
            System.out.println("Breakdown by worker:");
            float globalTimeS = (float) (end - start)/1_000_000_000L;

            stats.stream().filter(s -> s.getCount() > 0).forEach(s -> s.printStats(globalTimeS));
        }

    }
}
