package pt.haslab.mulletbench.queries.queryGenerators;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    protected String aggMinRange, aggMaxRange;
    protected float filterMinPercent, filterMaxPercent;
    protected String filterMinRange, filterMaxRange;
    protected float downsampleMinPercent, downsampleMaxPercent;
    protected String downsampleMinRange, downsampleMaxRange;
    protected float outlierMinPercent, outlierMaxPercent;
    protected String outlierMinRange, outlierMaxRange;

    // Probability weights for each query type
    int aggChance;
    int filterChance;
    int downsampleChance;
    int outlierChance;

    boolean countOutlierFilter;
    float filterZScore;
    long querySeed;
    boolean useSeed;
    
    private static final Pattern DURATION_PATTERN = Pattern.compile("^([0-9]+)\s*([a-zA-Z]+)$");

    QueryGenerator(QueryBuilder builder, ClientOptions options, TimeController tc, DatasetProcessor datasetProcessor) {
        this.builder = builder;
        this.from = switch (options.target) {
            case "influx" ->
                options.influx.bucket;
            case "iotdb" ->
                options.iotdb.devicePath;
            default ->
                throw new IllegalStateException("Unexpected value: " + options.target);
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
        if (this.useSeed) {
            random.setSeed(querySeed);
        }
        else {
            this.querySeed = System.currentTimeMillis();
            options.query.querySeed = this.querySeed;
            random.setSeed(querySeed);
        }
        this.aggMinPercent = options.query.aggMinPercent;
        this.aggMaxPercent = options.query.aggMaxPercent;
        this.aggMinRange = options.query.aggMinRange;
        this.aggMaxRange = options.query.aggMaxRange;
        this.filterMinPercent = options.query.filterMinPercent;
        this.filterMaxPercent = options.query.filterMaxPercent;
        this.filterMinRange = options.query.filterMinRange;
        this.filterMaxRange = options.query.filterMaxRange;
        this.downsampleMinPercent = options.query.downsampleMinPercent;
        this.downsampleMaxPercent = options.query.downsampleMaxPercent;
        this.downsampleMinRange = options.query.downsampleMinRange;
        this.downsampleMaxRange = options.query.downsampleMaxRange;
        this.outlierMinPercent = options.query.outlierMinPercent;
        this.outlierMaxPercent = options.query.outlierMaxPercent;
        this.outlierMinRange = options.query.outlierMinRange;
        this.outlierMaxRange = options.query.outlierMaxRange;
    }

    public void incrementSeed(long increment) {
        if (this.useSeed) {
            this.querySeed += increment;
            random.setSeed(querySeed);
        }
    }

    public long getCurrentSeed() {
        return this.querySeed;
    }

    public static QueryGenerator getInstance(String dataset, QueryBuilder builder, ClientOptions options, TimeController tc, DatasetProcessor datasetProcessor) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> clazz = Class.forName("pt.haslab.mulletbench.queries.queryGenerators.QueryGenerator");
        if (QueryGenerator.class.isAssignableFrom(clazz)) {
            return (QueryGenerator) clazz.getConstructor(QueryBuilder.class, ClientOptions.class, TimeController.class, DatasetProcessor.class).newInstance(builder, options, tc, datasetProcessor);
        } else {
            throw new ClassNotFoundException("Class does not extend QueryGenerator");
        }
    }

    protected long getRangeSize() {
        return timeController.getRangeSize();
    }

    protected Instant getRandomStart(Duration range) {
        long rangeSize = timeController.getRangeSize();
//        if(range.toMillis() > rangeSize)
//            throw new IllegalArgumentException("Range is larger than the total range of the dataset");
//        else if(range.toMillis() == rangeSize)
//            return timeController.getStartRange();

        return Instant.ofEpochMilli((long)(random.nextFloat() * (rangeSize - range.toMillis())) + timeController.getStartOfRange().toEpochMilli());
    }

    private ChronoUnit parseChronoUnit(String unitString) {
        return switch (unitString.toLowerCase()) {
            case "s" ->
                ChronoUnit.SECONDS;
            case "m" ->
                ChronoUnit.MINUTES;
            case "h" ->
                ChronoUnit.HOURS;
            case "d" ->
                ChronoUnit.DAYS;
            case "mo" ->
                ChronoUnit.MONTHS;
            case "y" ->
                ChronoUnit.YEARS;
            default ->
                throw new IllegalArgumentException("Invalid step unit: " + unitString);
        };
    }


    private Duration parseDurationString(String durationString) {
        Objects.requireNonNull(durationString, "Duration string cannot be null");
        durationString = durationString.trim();
        if (durationString.isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be empty");
        }

        Matcher matcher = DURATION_PATTERN.matcher(durationString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration format: " + durationString);
        }

        long amount = Long.parseLong(matcher.group(1));
        ChronoUnit unit = parseChronoUnit(matcher.group(2).toLowerCase());

        return Duration.of(amount, unit);
    }


    private Duration getRangeWithinBounds(String minimumRange, String maximumRange) {
        Duration minRange = parseDurationString(minimumRange);
        Duration maxRange = parseDurationString(maximumRange);

        if (minRange.compareTo(maxRange) > 0) {
            throw new IllegalArgumentException("Minimum range cannot be greater than maximum range");
        }

        long rangeSize = ThreadLocalRandom.current().nextLong(minRange.toMillis(), maxRange.toMillis() + 1);

        return Duration.ofMillis(rangeSize);
    }

    protected Duration getRandomRange(float minPercent, float maxPercent, String minimumRange, String maximumRange) {
        boolean hasPercent = minPercent > 0 && maxPercent > 0;
        boolean hasRanges = minimumRange != null && !minimumRange.isEmpty()
                && maximumRange != null && !maximumRange.isEmpty();

        if (!hasPercent && hasRanges) {
            // only min/max ranges
            return getRangeWithinBounds(minimumRange, maximumRange);
        }

        float percent = minPercent + random.nextFloat() * (maxPercent - minPercent);
        long percentAsLong = (long) (percent * 10_000);
        long rangeSize = (getRangeSize() * percentAsLong) / 10_000;
        Duration queryRange = Duration.ofMillis(rangeSize);

        if (hasRanges) {
            Duration minRange = parseDurationString(minimumRange);
            Duration maxRange = parseDurationString(maximumRange);

            if (queryRange.compareTo(minRange) < 0) {
                queryRange = minRange;
            } else if (queryRange.compareTo(maxRange) > 0) {
                queryRange = maxRange;
            }
        }

        return queryRange;
    }

    protected String getRandomColumn() {
        return this.columns.get(random.nextInt(this.columns.size()));
    }

    protected AggregationFunction getRandomAggregatorFunction() {
        return AggregationFunction.values()[random.nextInt(AggregationFunction.values().length)];
    }

    protected abstract Query generateAggregation();

    protected abstract Query generateFilter();

    protected abstract Query generateOutlierFilter();

    public abstract Query generateQuery();

    public abstract Query getFirstRecordQuery();

    public abstract Query getLastRecordQuery();

    protected void processTimestamp(Long timestamp) {
        timeController.processTimestamp(timestamp);
    }

    public void setStart(long timestamp) {
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
