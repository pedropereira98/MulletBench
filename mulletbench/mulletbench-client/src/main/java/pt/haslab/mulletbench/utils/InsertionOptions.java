package pt.haslab.mulletbench.utils;

public class InsertionOptions {

    public int batchSize = 1000;
    public long workerSectionSize = 5000;
    public float rate = 20;

    public InsertionOptions() {
    }

    public InsertionOptions(int batchSize) {
        this.batchSize = batchSize;
    }
}
