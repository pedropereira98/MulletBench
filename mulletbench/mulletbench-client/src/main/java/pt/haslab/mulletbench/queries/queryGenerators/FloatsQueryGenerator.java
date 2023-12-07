package pt.haslab.mulletbench.queries.queryGenerators;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.OperationType;
import pt.haslab.mulletbench.queries.AggregationFunction;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.queries.queryBuilders.QueryBuilder;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;
import pt.haslab.mulletbench.utils.ClientOptions;


// For datasets with float values
// To be extended by class with specific columns and parsing
public abstract class FloatsQueryGenerator extends QueryGenerator {
    private static final Logger logger = LogManager.getLogger();


    private final double[] sums;
    private final double[] squaredSums;

    private final double[] averages;
    private final double[] standardDeviations;
    // median would also be interesting.
    // how to calculate a median in a stream?

    private final float[] mins;
    private final float[] maxs;


    public FloatsQueryGenerator(QueryBuilder builder, ClientOptions options, int numFields, TimeController tc) {
        super(builder, options, tc);

        sums = new double[numFields];
        squaredSums = new double[numFields];
        averages = new double[numFields];
        standardDeviations = new double[numFields];
        mins = new float[numFields];
        maxs = new float[numFields];

        for (int i = 0; i < numFields; i++) {
            sums[i] = 0;
            squaredSums[i] = 0;
            mins[i] = Float.MAX_VALUE;
            maxs[i] = Float.MAX_VALUE * -1;
        }
    }


    protected float getRandomValue(int fieldIdx){
        return (float) (this.averages[fieldIdx] + this.standardDeviations[fieldIdx] * (random.nextFloat() * 4 - 2)); //get random value between -2 and 2 standard deviations from the mean
    }

//    returns a random number close to either the maximum (if over is True) or minimum (if over is False) for field with provided index
    protected float getRandomOutlier(int fieldIdx, boolean over){
        double avg = this.averages[fieldIdx];
        double stdDev = this.standardDeviations[fieldIdx];

        if(over){
            return (float) ((this.filterZScore * stdDev) + avg);
        } else {
            return (float) ((-1 * this.filterZScore * stdDev) + avg);
        }
    }

    // Aggregates values of random field in a time range between 5 seconds and 20 minutes (depending on range size)
    // into a value (with random aggregation function)
    protected Query generateAggregation(){
        String field = getRandomColumn(); //get random field from columns

        // queryRange is at least 5 seconds and at most 20 minutes
        Duration minimumRange = Duration.ofSeconds(5);
        Duration maximumRange = Duration.ofMinutes(20);
        Duration queryRange = Duration.ofMillis(getRangeSize()/4);

        if (queryRange.compareTo(minimumRange) < 0){
            queryRange = minimumRange;
        } else if (queryRange.compareTo(maximumRange) > 0){
            queryRange = maximumRange;
        }

        Instant before = getRandomStart(queryRange);
        Instant after = before.plus(queryRange);

        AggregationFunction function = getRandomAggregatorFunction();

        // System.out.println("Querying agg " + field + " from " + before + " to " + after + " with " + function);
        return new Query(builder.selectFrom(field, from)
                      .range(before, after)
                      .aggregation(function)
                      .query(), OperationType.AGGREGATION);
    }

    // Filters values for a field in time ranges between 5 seconds and 20 minutes (depending on the range of data)
    protected Query generateFilter(){
        String field = getRandomColumn(); //get random field from columns
        int fieldIdx = columns.indexOf(field);

        // queryRange is at least 5 seconds and at most 20 minutes
        Duration minimumRange = Duration.ofSeconds(5);
        Duration maximumRange = Duration.ofMinutes(20);
        Duration queryRange = Duration.ofMillis(getRangeSize()/4);

        if (queryRange.compareTo(minimumRange) < 0){
            queryRange = minimumRange;
        } else if (queryRange.compareTo(maximumRange) > 0){
            queryRange = maximumRange;
        }

        Instant before = getRandomStart(queryRange);
        Instant after = before.plus(queryRange);

//        logger.debug("Starts after startRange: " + before.isAfter(startRange) + " and before endRange: " + before.isBefore(endRange));

        // Float value = random.nextFloat() * 200 - 100; //get random value between -100 and 100
        float value = getRandomValue(fieldIdx);

        String operator = List.of("<",">").get(random.nextInt(2));

        // System.out.println("Querying filter " + field + " from " + before + " to " + after);
        return new Query(builder.selectFrom(field, from)
                      .range(before, after)
                      .filter(operator, Float.toString(value))
                      .query(), OperationType.FILTER);
    }

    // Finds extreme values for a field in time ranges between 5 seconds and 20 minutes (depending on the range of data)
    protected Query generateOutlierFilter(){
        String field = getRandomColumn(); //get random field from columns
        int fieldIdx = columns.indexOf(field);

        // queryRange is at least 5 seconds and at most 20 minutes
        Duration minimumRange = Duration.ofSeconds(5);
        Duration maximumRange = Duration.ofMinutes(20);
        Duration queryRange = Duration.ofMillis(getRangeSize()/4);

        if (queryRange.compareTo(minimumRange) < 0){
            queryRange = minimumRange;
        } else if (queryRange.compareTo(maximumRange) > 0){
            queryRange = maximumRange;
        }

        Instant before = getRandomStart(queryRange);
        Instant after = before.plus(queryRange);

        boolean over = random.nextFloat() < 0.5F;
        float value = getRandomOutlier(fieldIdx, over);
        String operator = over ? ">" : "<";

        QueryBuilder qb = builder.selectFrom(field, from)
            .range(before, after)
            .filter(operator, Float.toString(value));

        if(countOutlierFilter){
           qb = qb.aggregation(AggregationFunction.COUNT);
        }
        return new Query(qb.query(), OperationType.OUTLIER_FILTER);
    }

    // Downsamples field for whole time range into steps of 15, 30, 45, 60 or 120 seconds
    // with random aggregation function
    protected Query generateDownsampling(){
        // TODO influx and iotdb downsampling have different behaviours
        // iotdb starts from beginning of range and samples for every window (e.g. starts at 12:32:15 and ends at 13:02:15)
        // influx ends at the first "flat" timestamp after beginning of range (e.g. starts at 12:32:15 and ends at 13:00:00 for 30 minute range)

        String field = getRandomColumn(); //get random field from columns

        AggregationFunction function = getRandomAggregatorFunction();

        //TODO improve downsampling ranges to be more realistic
        ChronoUnit unit = List.of(ChronoUnit.MINUTES, ChronoUnit.SECONDS).get(random.nextInt(2));

        int stepAmount = switch(unit) {
            case MINUTES -> List.of(1,2).get(random.nextInt(2));
            case SECONDS -> List.of(15, 30).get(random.nextInt(2));
            default -> throw new IllegalStateException("Unexpected value: " + unit);
        };

        // queryRange is at least 5 seconds and at most 20 minutes
        Duration minimumRange = Duration.ofMinutes(1);
        Duration maximumRange = Duration.ofMinutes(120);
        Duration queryRange = Duration.ofMillis(getRangeSize()/4);

        if (queryRange.compareTo(minimumRange) < 0){
            queryRange = minimumRange;
        } else if (queryRange.compareTo(maximumRange) > 0){
            queryRange = maximumRange;
        }

        Instant before = getRandomStart(queryRange);
        Instant after = before.plus(queryRange);

        return new Query(builder.selectFrom(field, from)
                      .downsample(function, before, after, stepAmount, unit)
                      .query(), OperationType.DOWNSAMPLING);
    }

    @Override
    // This is synchronized since every worker shares one instance of this class
    // Possibly might be better to have one instance per worker
    public synchronized Query generateQuery(){
        builder.reset();

        int choice = random.nextInt(this.outlierChance);

        if(choice < this.aggChance){
            return generateAggregation();
        } else if (choice < this.filterChance){
            return generateFilter();
        } else if (choice < this.downsampleChance) {
            return generateDownsampling();
        } else if (choice < this.outlierChance) {
            return generateOutlierFilter();
        }else {
            logger.error("Invalid choice: " + choice);
            throw new RuntimeException("Invalid choice");
        }
    }

    protected void processValue(Float value, int column){
        sums[column] += value;
        squaredSums[column] += value * value;
        maxs[column] = Math.max(maxs[column], value);
        mins[column] = Math.min(mins[column], value);
    }

    protected void processFinish(int count){
        // use sum, squaredSums and count to calculate average and std dev. for each column
        for(int i = 0; i < columns.size(); i++){
            averages[i] = sums[i]/count;
            standardDeviations[i] = Math.sqrt(squaredSums[i]/count - averages[i]*averages[i]);
        }

        logger.debug("Processed " + count + " lines");

        for(String column : columns){
            logger.debug("Column " + column + " average: " + averages[columns.indexOf(column)]);
            logger.debug("Column " + column + " standard deviation: " + standardDeviations[columns.indexOf(column)]);
            logger.debug("Column " + column + " min: " + mins[columns.indexOf(column)]);
            logger.debug("Column " + column + " max: " + maxs[columns.indexOf(column)]);
        }

        timeController.processFinish(logger);

    }
}
