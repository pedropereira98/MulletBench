package pt.haslab.mulletbench.queries.queryBuilders;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import pt.haslab.mulletbench.queries.AggregationFunction;

public class IoTDBQueryBuilder extends QueryBuilder {

    private String select;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx").withZone(ZoneId.systemDefault());
    private boolean inWhere;
    private final boolean alignByDevice;

    public IoTDBQueryBuilder(boolean alignByDevice){
        this.query = new StringBuilder();
        this.select = "";
        this.inWhere = false;
        this.alignByDevice = alignByDevice;
    }

    @Override
    public String query(){
        if(alignByDevice){
            this.query.append(" align by device");
        }
        return super.query();
    }

    @Override
    public QueryBuilder filter(String operator, String value) {
        return filter(this.select, operator, value);
    }

    @Override
    public QueryBuilder filter(String field, String operator, String value) {
        String filter;
        if(this.inWhere){
            filter = " and %s %s %s";
        } else {
            filter = " where %s %s %s";
        }
        this.query.append(String.format(filter, field, operator, value));
        this.inWhere = true;
        return this;
    }

    @Override
    public QueryBuilder select(String select) {
        this.select = "\\*";
        this.query.append(String.format("select * from %s", select));
        this.inWhere = false;
        return this;
    }

    @Override
    public QueryBuilder selectFrom(String field, String from) {
        this.select = field;
        this.query.append(String.format("select %s from %s", field, from));
        this.inWhere = false;
        return this;
    }



    @Override
    protected String parseAggregation(AggregationFunction agg) {
        return switch (agg) {
            case MEAN -> "avg";
            case SUM -> "sum";
            case COUNT -> "count";
            case MIN -> "MIN_VALUE";
            case MAX -> "MAX_VALUE";
            default -> throw new IllegalArgumentException("Invalid aggregation function for IoTDB");
        };
    }

    @Override
    public QueryBuilder aggregation(AggregationFunction agg) throws IllegalArgumentException {
        String to = parseAggregation(agg)+"("+this.select+")";
        int index = this.query.indexOf(this.select);
        this.query.replace(index, index + this.select.length(), to);
        return this;
    }

    @Override
    public QueryBuilder range(Instant before, Instant after) {
        String range;
        if(this.inWhere){
            range = " and time >= %s and time <= %s";
        } else {
            range = " where time >= %s and time <= %s";
        }
        this.query.append(String.format(range, formatter.format(before), formatter.format(after)));
        this.inWhere = true;
        return this;
    }

    @Override
    public QueryBuilder rangeAfter(Instant time) {
        String range;
        if(this.inWhere){
            range = " and time >= %s";
        } else {
            range = " where time >= %s";
        }
        this.query.append(String.format(range, formatter.format(time)));
        this.inWhere = true;
        return this;
    }

    @Override
    public QueryBuilder rangeBefore(Instant time) {
        String range;
        if(this.inWhere){
            range = " and time <= %s";
        } else {
            range = " where time <= %s";
        }
        this.query.append(String.format(range, formatter.format(time)));
        this.inWhere = true;
        return this;
    }



    @Override
    public QueryBuilder downsample(AggregationFunction aggFunction, Instant start, Instant end, int stepAmount, ChronoUnit stepUnit) {
        this.aggregation(aggFunction);
        String step = String.format("%d%s", stepAmount, parseChronoUnit(stepUnit));

        this.query.append(String.format(" group by ([%s, %s), %s)", formatter.format(start), formatter.format(end), step));

        return this;
    }

    @Override
    public void reset() {
        this.query = new StringBuilder();
        this.select = "";
    }

}
