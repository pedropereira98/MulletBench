package pt.haslab.mulletbench.queries.queryBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import pt.haslab.mulletbench.queries.AggregationFunction;

public class InfluxQueryBuilder extends QueryBuilder {
    private String from;

    public InfluxQueryBuilder(){
        this.from = "";
        this.query = new StringBuilder();
    }

    public QueryBuilder select(String select){
        this.from = String.format("from(bucket: \"%s\")", select);
        return this;
    }

    @Override
    public QueryBuilder selectFrom(String field, String from) {
        this.from = String.format("from(bucket: \"%s\")", from);
        this.query.append(String.format(" |> filter(fn: (r) => r._field == \"%s\")", field));
        return this;
    }

    protected String parseAggregation(AggregationFunction agg){
        return switch (agg) {
            case MEAN -> "mean";
            case SUM -> "sum";
            case COUNT -> "count";
            case MIN -> "min";
            case MAX -> "max";
        };
    }

    public QueryBuilder aggregation(AggregationFunction agg) throws IllegalArgumentException{
        this.query.append(String.format(" |> %s()", parseAggregation(agg)));
        return this;
    }

    @Override
    public QueryBuilder filter(String operator, String value) {
        this.query.append(String.format(" |> filter(fn: (r) => r._value %s %s)", operator, value));
        return this;
    }

    public QueryBuilder filter(String field, String operator, String value){
        this.query.append(String.format(" |> filter(fn: (r) => r.%s %s %s)", field, operator, value));
        return this;
    }

    public QueryBuilder range(Instant before, Instant after){
        this.query.insert(0, String.format(" |> range(start: %s, stop: %s)", before.toString(), after.toString())); // range must be the first function after from
        return this;
    }

    public QueryBuilder rangeAfter(Instant time){
        this.query.insert(0, String.format(" |> range(start: %s)", time.toString())); // range must be the first function after from
        return this;
    }

    public QueryBuilder rangeBefore(Instant time){
        this.query.insert(0, String.format(" |> range(stop: %s)", time.toString())); // range must be the first function after from
        return this;
    }

    @Override
    public QueryBuilder downsample(AggregationFunction aggFunction, Instant start, Instant end, int stepAmount,
            ChronoUnit stepUnit) {
        String step = String.format("%d%s", stepAmount, parseChronoUnit(stepUnit));

        range(start, end);
        this.query.append(String.format(" |> aggregateWindow(every: %s, fn: %s)", step, parseAggregation(aggFunction)));
        return this;
    }

    public void reset(){
        this.from = "";
        this.query = new StringBuilder();
    }

    @Override
    public String query() {
        return new String(from + query.toString());
    }


}
