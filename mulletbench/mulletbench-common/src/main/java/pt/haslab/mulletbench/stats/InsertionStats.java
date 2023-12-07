package pt.haslab.mulletbench.stats;

// Insertion stats for a single worker
public class InsertionStats extends Stats {

    public InsertionStats() {
        super();
    }


    @Override
    protected int getCount() {
        return (int) this.operations.stream().filter(statsEntry -> statsEntry.type().isValidInsert()).count();
    }

    @Override
    protected int getVolume() {
        return operations.stream().filter(statsEntry -> statsEntry.type().isValidInsert()).mapToInt(StatsEntry::getCount).sum();
    }

    public void printStats(float globalTimeS){
        final long writeTimeMS = this.getTime()/1_000_000L; //TODO Should this be write time or clock time?
        final int volume = this.getVolume();
        final int count = this.getCount();
        System.out.println();
        System.out.println("Total time: " + globalTimeS + " seconds");
        System.out.println("Inserted volume: " + volume);
        System.out.println("Insert count: " + count);
        if(getFailedInsertCount() > 0){
            System.out.println("Failed insert count: " + this.getFailedInsertCount());
        }
        System.out.println("Insertion rate: " + (float) volume / globalTimeS + " inserts/s");
        System.out.println("Insertion rate: " + (float) count / globalTimeS + " insertion ops/s");
        if(count > 0){
            System.out.println("Average latency: " + writeTimeMS/count + "ms");
        }
    }

    private int getFailedInsertCount() {
        return (int) this.operations.stream().filter(statsEntry -> statsEntry.type().isFailedInsert()).count();
    }
}
