package pt.haslab.mulletbench.utils;

public class QueryOptions {

    public float rate = 10;
    public int count = 50;
    public int aggChance = 25;
    public int filterChance = 25;
    public int downsampleChance = 25;
    public int outlierChance = 25;
    public float numberOfLoops = 1.0F;
    public boolean countOutlierFilter = true;
    public float filterZScore = 4.0F;

    public QueryOptions() {
    }
}
