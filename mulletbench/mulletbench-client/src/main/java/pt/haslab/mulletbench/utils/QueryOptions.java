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
    public float aggMinPercent = -1.0F;
    public float aggMaxPercent = -1.0F;
    public String aggMinRange = "";
    public String aggMaxRange = "";
    public float downsampleMinPercent = -1.0F;
    public float downsampleMaxPercent = -1.0F;
    public String downsampleMinRange = "";
    public String downsampleMaxRange = "";
    public float outlierMinPercent = -1.0F;
    public float outlierMaxPercent = -1.0F;
    public String outlierMinRange = "";
    public String outlierMaxRange = "";
    public float filterMinPercent = -1.0F;
    public float filterMaxPercent = -1.0F;
    public String filterMinRange = "";
    public String filterMaxRange = "";

    public QueryOptions() {
    }
}
