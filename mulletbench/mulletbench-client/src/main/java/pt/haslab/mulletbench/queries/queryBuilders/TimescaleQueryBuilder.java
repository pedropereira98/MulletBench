package pt.haslab.mulletbench.queries.queryBuilders;

import pt.haslab.mulletbench.queries.AggregationFunction;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class TimescaleQueryBuilder extends QueryBuilder{

    private String select;
    private String groupBy = "";
    private boolean aggregated = false;
    private Boolean inWhere = false;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public TimescaleQueryBuilder(){
        this.select = "";
        this.query = new StringBuilder();
    }

    @Override
    public QueryBuilder select(String select) {
        this.select = "\\*";
        this.query.append(String.format("select value, device_id, sensor_id from %s", select));
        return this;
    }

    @Override
    public QueryBuilder selectFrom(String field, String from) {
        this.select = "value";
        this.query.append(String.format("select value, device_id, sensor_id from %s where sensor_id = %s", from, field));
        this.inWhere = true;
        return this;
    }

    @Override
    public QueryBuilder aggregation(AggregationFunction aggFunction) {
//      select x -> select affFunction(x)
        String to = parseAggregation(aggFunction)+"("+this.select+")";
        int index = this.query.indexOf(this.select);
        this.query.replace(index, index + this.select.length(), to);
        this.groupBy = " group by device_id, sensor_id";
        this.aggregated = true;
        this.select = to;
        return this;
    }

    @Override
    public QueryBuilder filter(String operator, String value) {
        String filter;
        if(this.inWhere){
            filter = " and value %s %s";
        } else {
            filter = " where value %s %s";
        }
        this.query.append(String.format(filter, operator, value));
        this.inWhere = true;
        return this;
    }

    @Override
    public QueryBuilder filter(String field, String operator, String value) {
        String filter;
        if(this.inWhere){
            filter = " and sensor_id = %s and value %s %s";
        } else {
            filter = " where sensor_id = %s and value %s %s";
        }
        this.query.append(String.format(filter, field, operator, value));
        this.inWhere = true;
        return this;
    }




    @Override
    public QueryBuilder range(Instant before, Instant after) {
        return this.rangeBefore(after).rangeAfter(before);
    }

    @Override
    public QueryBuilder rangeAfter(Instant time) {
        String range;
        if(this.inWhere){
            range = " and time >= '%s'";
        } else {
            range = " where time >= '%s'";
        }
        this.query.append(String.format(range, formatter.format(time)));
        this.inWhere = true;
        return this;
    }

    @Override
    public QueryBuilder rangeBefore(Instant time) {
        String range;
        if(this.inWhere){
            range = " and time <= '%s'";
        } else {
            range = " where time <= '%s'";
        }
        this.query.append(String.format(range, formatter.format(time)));
        this.inWhere = true;
        return this;
    }

    @Override
    protected String parseChronoUnit(ChronoUnit unit){
        return switch (unit) {
            case SECONDS -> "seconds";
            case MINUTES -> "minutes";
            case HOURS -> "hours";
            case DAYS -> "days";
            case MONTHS -> "months";
            case YEARS -> "years";
            default -> throw new IllegalArgumentException("Invalid step unit");
        };
    }


//    Downsampling example
//    SELECT time_bucket('15 seconds', time) AS time_bucket,
//    device_id, sensor_id,
//    AVG(value) AS avg_value
//    FROM sensor_data
//    WHERE time > NOW() - INTERVAL '1 hours'
//    GROUP BY time_bucket, sensor_id, device_id
//    ORDER BY time_bucket ASC, sensor_id ASC;
    @Override
    public QueryBuilder downsample(AggregationFunction aggFunction, Instant start, Instant end, int stepAmount, ChronoUnit stepUnit) {
        this.aggregation(aggFunction);
        //        add time_bucket to select
        String timeBucket = String.format("time_bucket('%d %s', time) as time_bucket, ", stepAmount, parseChronoUnit(stepUnit));

        int index = this.query.indexOf(this.select);
        this.query.replace(index, index + this.select.length(), timeBucket + this.select);
        //      add group by time_bucket

        this.range(start, end);
        this.groupBy = " group by time_bucket, sensor_id, device_id";
        return this;
    }

    @Override
    public void reset() {
        this.query = new StringBuilder();
        this.groupBy = "";
        this.aggregated = false;
        this.inWhere = false;
    }

    @Override
    protected String parseAggregation(AggregationFunction agg) {
        return switch (agg) {
            case MEAN -> "avg";
            case SUM -> "sum";
            case COUNT -> "count";
            case MIN -> "MIN";
            case MAX -> "MAX";
            default -> throw new IllegalArgumentException("Invalid aggregation function for IoTDB");
        };
    }

    @Override
    public String query(){
        if(!aggregated){
            String to = String.format("time, %s", this.select);
            int index = this.query.indexOf(this.select);
            this.query.replace(index, index + this.select.length(), to);
        }
        this.query.append(this.groupBy);
        return this.query.toString();
    }

    public static void main(String[] args) {
        TimescaleQueryBuilder tsqb = new TimescaleQueryBuilder();
        String from = "sensor_data";
        String field = "1";
        Float value = Float.valueOf("1.2");
        Instant before = Instant.now().minus(ChronoUnit.HOURS.getDuration().multipliedBy(28));
        Instant after = Instant.now();
        AggregationFunction aggregationFunction = AggregationFunction.COUNT;

//        String query = tsqb.selectFrom(field, from)
//                      .range(before, after)
//                      .aggregation(aggregationFunction)
//                      .query();

            String query = tsqb.selectFrom(field, from)
            .range(before, after)
            .filter(">=", Float.toString(value))
            .query();


//        String query = tsqb.selectFrom(field, from)
//            .range(before, after)
//            .filter(">=", Float.toString(value))
//            .aggregation(aggregationFunction)
//            .query();

//        String query = tsqb.selectFrom(field, from)
//            .downsample(aggregationFunction, before, after, 5, ChronoUnit.SECONDS)
//            .query();

        System.out.println(query);
//        System.out.println(tsqb.selectFrom(field,from).filter(field, ">", String.valueOf(10)).query());
    }
}
