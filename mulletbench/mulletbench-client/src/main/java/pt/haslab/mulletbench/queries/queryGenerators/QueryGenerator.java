package pt.haslab.mulletbench.queries.queryGenerators;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import pt.haslab.mulletbench.queries.AggregationFunction;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.queries.queryBuilders.QueryBuilder;
import pt.haslab.mulletbench.queries.queryGenerators.datasetProcessors.DatasetProcessor;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;
import pt.haslab.mulletbench.utils.ClientOptions;

public abstract class QueryGenerator {
    protected QueryBuilder builder;

    protected DatasetProcessor datasetProcessor;

    protected String from; //bucket or devicePath

    protected TimeController timeController;

    protected List<String> columns;

    protected Random random;

    protected Instant startRange, endRange; // start and end of the range of the dataset
    
    protected float aggMinPercent, aggMaxPercent;
    protected float filterMinPercent, filterMaxPercent;
    protected float downsampleMinPercent, downsampleMaxPercent;
    protected float outlierMinPercent, outlierMaxPercent;
    
    // Probability weights for each query type
    int aggChance;
    int filterChance;
    int downsampleChance;
    int outlierChance;

    boolean countOutlierFilter;
    float filterZScore;
    long querySeed;
    boolean useSeed;


    QueryGenerator(QueryBuilder builder, ClientOptions options, TimeController tc, DatasetProcessor datasetProcessor) {
        this.builder = builder;
        this.from = switch (options.target) {
            case "influx" ->  options.influx.bucket;
            case "iotdb" -> options.iotdb.devicePath;
            default -> throw new IllegalStateException("Unexpected value: " + options.target);
        };
        this.random = new Random();
        
        this.columns = datasetProcessor.getColumns();
        this.aggChance = options.query.aggChance;
        this.filterChance = options.query.filterChance + aggChance;
        this.downsampleChance = options.query.downsampleChance + filterChance;
        this.outlierChance = options.query.outlierChance + downsampleChance;
        this.timeController = tc;
        this.countOutlierFilter = options.query.countOutlierFilter;
        this.filterZScore = options.query.filterZScore;
        this.querySeed = options.query.querySeed;
        this.useSeed = true;
        if (this.querySeed == -1L) {
            this.useSeed = false;
        }
        if (this.useSeed){
            random.setSeed(querySeed);
        }
        this.aggMinPercent = options.query.aggMinPercent;
        this.aggMaxPercent = options.query.aggMaxPercent;
        this.filterMinPercent = options.query.filterMinPercent;
        this.filterMaxPercent = options.query.filterMaxPercent;
        this.downsampleMinPercent = options.query.downsampleMinPercent;
        this.downsampleMaxPercent = options.query.downsampleMaxPercent;
        this.outlierMinPercent = options.query.outlierMinPercent;
        this.outlierMaxPercent = options.query.outlierMaxPercent;
        // print percentages for debugging purposes
        System.out.println("Aggregation Percentages: " + aggMinPercent + " - " + aggMaxPercent);
        System.out.println("Filter Percentages: " + filterMinPercent + " - " + filterMaxPercent);
        System.out.println("Downsample Percentages: " + downsampleMinPercent + " - " + downsampleMaxPercent);
        System.out.println("Outlier Percentages: " + outlierMinPercent + " - " + outlierMaxPercent);
    }

    public void incrementSeed(long increment) {
        if (this.useSeed) {
            this.querySeed += increment;
            random.setSeed(querySeed);
        }
    }

    public static QueryGenerator getInstance(String dataset, QueryBuilder builder, ClientOptions options, TimeController tc, DatasetProcessor datasetProcessor) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> clazz = Class.forName("pt.haslab.mulletbench.queries.queryGenerators.QueryGenerator");
        if (QueryGenerator.class.isAssignableFrom(clazz)) {
            return (QueryGenerator) clazz.getConstructor(QueryBuilder.class, ClientOptions.class, TimeController.class, DatasetProcessor.class).newInstance(builder, options, tc, datasetProcessor);
        } else {
            throw new ClassNotFoundException("Class does not extend QueryGenerator");
        }
    }

    protected long getRangeSize(){
        return timeController.getRangeSize();
    }

    protected Instant getRandomStart(Duration range){
        long rangeSize = timeController.getRangeSize();
//        if(range.toMillis() > rangeSize)
//            throw new IllegalArgumentException("Range is larger than the total range of the dataset");
//        else if(range.toMillis() == rangeSize)
//            return timeController.getStartRange();

        return Instant.ofEpochMilli(Math.abs(random.nextLong()) % (rangeSize - range.toMillis()) + timeController.getStartOfRange().toEpochMilli());
    }

    protected Duration getRandomRange(float minPercent, float maxPercent, Duration minimumRange, Duration maximumRange) {
        
        float percent = minPercent + random.nextFloat() * (maxPercent - minPercent);

        long rangeSize = (getRangeSize() * ((long) percent * 100)) / 100;

        Duration queryRange = Duration.ofMillis(rangeSize);

        if (queryRange.compareTo(minimumRange) < 0){
            queryRange = minimumRange;
        } else if (queryRange.compareTo(maximumRange) > 0){
            queryRange = maximumRange;
        }

        return queryRange;
    }

    protected String getRandomColumn(){
        return this.columns.get(random.nextInt(this.columns.size()));
    }

    protected AggregationFunction getRandomAggregatorFunction(){
        return AggregationFunction.values()[random.nextInt(AggregationFunction.values().length)];
    }

    protected abstract Query generateAggregation();

    protected abstract Query generateFilter();

    protected abstract Query generateOutlierFilter();

    public abstract Query generateQuery();

    public abstract Query getFirstRecordQuery();

    public abstract Query getLastRecordQuery();

    protected void processTimestamp(Long timestamp){
        timeController.processTimestamp(timestamp);
    }

    public void setStart(long timestamp){
        timeController.setStart(timestamp);
    }

    // Generate queries before execution to reduce overhead
    public List<Query> generateQueries(int count) {
        return IntStream.range(0, count).mapToObj(__ -> generateQuery()).collect(Collectors.toList());
    }

    public long getSeed() {
        return this.querySeed;
    }
}
