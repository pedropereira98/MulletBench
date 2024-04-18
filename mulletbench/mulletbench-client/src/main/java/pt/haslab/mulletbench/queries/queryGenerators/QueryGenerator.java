package pt.haslab.mulletbench.queries.queryGenerators;

import pt.haslab.mulletbench.queries.AggregationFunction;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.queries.queryBuilders.QueryBuilder;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;
import pt.haslab.mulletbench.utils.ClientOptions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class QueryGenerator {
    protected QueryBuilder builder;

    protected String from; //bucket or devicePath

    protected TimeController timeController;

    protected List<String> columns;

    protected Random random;

    private Boolean useFieldIndex = false;

    // Probability weights for each query type
    int aggChance;
    int filterChance;
    int downsampleChance;
    int outlierChance;

    boolean countOutlierFilter;
    float filterZScore;

    QueryGenerator(QueryBuilder builder, ClientOptions options, TimeController tc){
        this.builder = builder;
        this.from = switch (options.target) {
            case "influx" ->  options.influx.bucket;
            case "iotdb" -> options.iotdb.devicePath;
            case "timescale" -> options.timescale.tableName;
            default -> throw new IllegalStateException("Unexpected value: " + options.target);
        };
        this.useFieldIndex = switch (options.target){
            case "timescale" -> true;
            default -> false;
        };
        this.random = new Random();

        this.aggChance = options.query.aggChance;
        this.filterChance = options.query.filterChance + aggChance;
        this.downsampleChance = options.query.downsampleChance + filterChance;
        this.outlierChance = options.query.outlierChance + downsampleChance;
        this.timeController = tc;
        this.countOutlierFilter = options.query.countOutlierFilter;
        this.filterZScore = options.query.filterZScore;

    }

    public static QueryGenerator getInstance(String dataset, QueryBuilder builder, ClientOptions options, TimeController tc) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> clazz = Class.forName("pt.haslab.mulletbench.queries.queryGenerators." + dataset + "QueryGenerator");
        if (QueryGenerator.class.isAssignableFrom(clazz)) {
            return (QueryGenerator) clazz.getConstructor(QueryBuilder.class, ClientOptions.class, TimeController.class).newInstance(builder, options, tc);
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

    protected String getRandomColumn(){
        int randomIdx = random.nextInt(this.columns.size());
        if(this.useFieldIndex) {
            return String.valueOf(randomIdx);
        }
        else {
            return this.columns.get(randomIdx);
        }
    }

    protected int columnIndex(String field){
        if(this.useFieldIndex){
            return Integer.parseInt(field);
        } else {
            return columns.indexOf(field);
        }
    }

    protected AggregationFunction getRandomAggregatorFunction(){
        return AggregationFunction.values()[random.nextInt(AggregationFunction.values().length)];
    }

    protected abstract Query generateAggregation();

    protected abstract Query generateFilter();

    protected abstract Query generateOutlierFilter();

    public abstract Query generateQuery();

    protected void processTimestamp(Long timestamp){
        timeController.processTimestamp(timestamp);
    }

    public void setStart(long timestamp){
        timeController.setStart(timestamp);
    }

    public abstract void process(String dataFile) throws IOException;

    // Generate queries before execution to reduce overhead
    public List<Query> generateQueries(int count) {
        return IntStream.range(0, count).mapToObj(__ -> generateQuery()).collect(Collectors.toList());
    }

}
