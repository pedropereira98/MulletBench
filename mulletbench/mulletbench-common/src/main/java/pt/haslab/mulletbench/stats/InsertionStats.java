package pt.haslab.mulletbench.stats;

import pt.haslab.mulletbench.IndentString;

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

    @Override
    public void printStats(float globalTimeS, int indentation) {
        final long writeTimeMS = this.getTime()/1_000_000L; //TODO Should this be write time or clock time?
        final int volume = this.getVolume();
        final int count = this.getCount();
        System.out.println();
        System.out.println(IndentString.indent(indentation) + "Total time: " + globalTimeS + " seconds");
        System.out.println(IndentString.indent(indentation) + "Inserted volume: " + volume);
        System.out.println(IndentString.indent(indentation) + "Insert count: " + count);
        if(getFailedInsertCount() > 0){
            System.out.println(IndentString.indent(indentation) + "Failed insert count: " + this.getFailedInsertCount());
        }
        System.out.println(IndentString.indent(indentation) + "Insertion rate: " + (float) volume / globalTimeS + " inserts/s");
        System.out.println(IndentString.indent(indentation) + "Insertion rate: " + (float) count / globalTimeS + " insertion ops/s");
        if(count > 0){
            System.out.println(IndentString.indent(indentation) + "Average latency: " + writeTimeMS/count + "ms");
        }
    }

    private int getFailedInsertCount() {
        return (int) this.operations.stream().filter(statsEntry -> statsEntry.type().isFailedInsert()).count();
    }
}
