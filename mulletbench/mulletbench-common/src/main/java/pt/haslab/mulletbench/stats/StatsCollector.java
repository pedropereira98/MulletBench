package pt.haslab.mulletbench.stats;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import pt.haslab.mulletbench.IndentString;
import pt.haslab.mulletbench.TimeProvider;
import pt.haslab.mulletbench.WorkloadType;

//The statsCollector as a whole is only accessed either before or after workload execution
public class StatsCollector implements Serializable {
    //List of insertion stats for each worker
    public final List<Stats> stats;
    public final WorkloadType type;
    public long querySeed;

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

    private void printInsertionStats(int indentation){
        InsertionStats globalStats = new InsertionStats();

        stats.forEach(globalStats::join);

        if(globalStats.getCount() > 0){
            final long globalTimeMS = (end - start)/ 1_000_000L;
            final long writeTimeMS =  stats.stream().mapToLong(Stats::getTime).sum()/ 1_000_000L;
            final float globalTimeS = (float) globalTimeMS / 1_000L;
    
            System.out.println();
            System.out.println(IndentString.indent(indentation) + "Global stats:");
            indentation++;
            System.out.println(IndentString.indent(indentation) + "Total global time: " + globalTimeMS + "ms");
            System.out.println(IndentString.indent(indentation) + "Total write time: " + writeTimeMS + "ms");

            globalStats.printStats(globalTimeS, indentation);
        }
    }

    private void printQueryStats(int indentation){
        QueryStats globalStats = new QueryStats();

        stats.forEach(globalStats::join);

        if(globalStats.getCount() > 0){
            final long globalTimeMS = (end - start)/ 1_000_000L;
            final long queryTimeMS =  stats.stream().mapToLong(Stats::getTime).sum()/ 1_000_000L;
            final float globalTimeS = (float) globalTimeMS / 1_000L;
    
            System.out.println();
            System.out.println(IndentString.indent(indentation) + "Global stats:");
            indentation++;
            System.out.println(IndentString.indent(indentation) + "Random Seed used: " + querySeed);
            System.out.println(IndentString.indent(indentation) + "Total global time: " + globalTimeMS + "ms");
            System.out.println(IndentString.indent(indentation) + "Total query time: " + queryTimeMS + "ms");

            globalStats.printStats(globalTimeS, indentation);
        }
    }

    public List<String> toCSV(){
        return stats.stream().map(Stats::toCSV).flatMap(List::stream).collect(Collectors.toList());
    }

    public void printStats(int indentation){
        switch(type){
            case INSERT:
                printInsertionStats(indentation);
                break;
            case QUERY:
                printQueryStats(indentation);
                break;
        }

        if(stats.size() <= 4){
            System.out.println("\n" + IndentString.indent(indentation) + "Breakdown by worker:");
            float globalTimeS = (float) (end - start)/1_000_000_000L;
            indentation++;

            List<Stats> filtered = stats.stream().filter(s -> s.getCount() > 0).collect(Collectors.toList());
            
            int id = 0;
            for (Stats s : filtered){
                System.out.println("\n" + IndentString.indent(indentation) + "Worker " + id + ":");
                indentation++;
                s.printStats(globalTimeS, indentation);
                indentation--;
                id++;
            }
            
        }

    }

    public void setQuerySeed(long seed){
        this.querySeed = seed;
    }
}
