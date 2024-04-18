package pt.haslab.mulletbench.queries.queryBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import pt.haslab.mulletbench.queries.AggregationFunction;
import pt.haslab.mulletbench.utils.ClientOptions;

public abstract class QueryBuilder {
    StringBuilder query;

    //Probably should not be used
    public abstract QueryBuilder select(String select);

    public abstract QueryBuilder selectFrom(String field, String from);

    public abstract QueryBuilder aggregation(AggregationFunction aggFunction);

    public abstract QueryBuilder filter(String operator, String value);

    public abstract QueryBuilder filter(String field, String operator, String value);

    public abstract QueryBuilder range(Instant before, Instant after);

    public abstract QueryBuilder rangeAfter(Instant time);

    public abstract QueryBuilder rangeBefore(Instant time);

    public abstract QueryBuilder downsample(AggregationFunction aggFunction, Instant start, Instant end, int stepAmount, ChronoUnit stepUnit);

    public abstract void reset();

    protected abstract String parseAggregation(AggregationFunction agg);

    protected String parseChronoUnit(ChronoUnit unit){
        return switch (unit) {
            case SECONDS -> "s";
            case MINUTES -> "m";
            case HOURS -> "h";
            case DAYS -> "d";
            case MONTHS -> "mo";
            case YEARS -> "y";
            default -> throw new IllegalArgumentException("Invalid step unit");
        };
    }

    public String query(){
        return this.query.toString();
    }

    public static QueryBuilder createQueryBuilder(ClientOptions options) throws IllegalArgumentException{
        return switch (options.target) {
            case "influx" -> new InfluxQueryBuilder();
            case "iotdb" -> new IoTDBQueryBuilder(options.iotdb.queryAlignByDevice);
            case "timescale" -> new TimescaleQueryBuilder();
            default -> throw new IllegalArgumentException("Invalid query generator name");
        };
    }
}
