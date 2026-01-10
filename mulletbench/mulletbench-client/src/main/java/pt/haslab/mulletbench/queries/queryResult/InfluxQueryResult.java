package pt.haslab.mulletbench.queries.queryResult;

import java.util.LinkedList;
import java.util.List;

import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;

public class InfluxQueryResult extends QueryResult{
    private final List<FluxTable> results;
    private List<String> resultStrings;

    public InfluxQueryResult(List<FluxTable> results) {
        this.results = results;
        this.resultStrings = null;
    }

    @Override
    public int size() {
        if (results == null)
            return 0;
        if (resultStrings == null)
            this.generateResultStrings();
        return resultStrings.size();
    }

    private void generateResultStrings() {
        resultStrings = new LinkedList<>();
        for (FluxTable fluxTable : results) {
            List<FluxRecord> records = fluxTable.getRecords();
            for (final FluxRecord fluxRecord : records) {
                logger.trace(fluxRecord.getTime() + ": " + fluxRecord.getValueByKey("_value"));
                StringBuilder sb = new StringBuilder();
                for (String key : fluxRecord.getValues().keySet()) {
                    sb.append(key).append("=").append(fluxRecord.getValueByKey(key)).append(" ");
                }
                resultStrings.add(sb.toString().trim());
            }
        }
    }

    @Override
    public List<String> getResultStrings() {
        if (resultStrings == null)
            this.generateResultStrings();
        return new LinkedList<>(resultStrings);
    }
}
