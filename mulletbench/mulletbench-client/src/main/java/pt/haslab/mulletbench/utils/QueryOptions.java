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
    public long querySeed = -1L;
    public float aggMinPercent = 0.1F;
    public float aggMaxPercent = 0.2F;
    public float downsampleMinPercent = 0.1F;
    public float downsampleMaxPercent = 0.2F;
    public float outlierMinPercent = 0.1F;
    public float outlierMaxPercent = 0.2F;
    public float filterMinPercent = 0.1F;
    public float filterMaxPercent = 0.2F;

    public QueryOptions() {
    }
}
