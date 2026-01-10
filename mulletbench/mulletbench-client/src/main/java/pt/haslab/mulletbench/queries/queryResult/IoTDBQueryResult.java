package pt.haslab.mulletbench.queries.queryResult;

import java.util.LinkedList;
import java.util.List;

import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.tsfile.read.common.RowRecord;

public class IoTDBQueryResult extends QueryResult {
    private final SessionDataSet resultSet;
    private List<String> resultStrings;

    public IoTDBQueryResult(SessionDataSet resultSet) {
        this.resultSet = resultSet;
        this.resultStrings = null;
    }

    @Override
    public int size() {
        if (resultSet == null)
            return 0;
        if (resultStrings == null)
            this.generateResultStrings();

        return this.resultStrings.size();
    }

    private void generateResultStrings() {
        resultStrings = new LinkedList<>();
        try {
            if (resultSet != null) {
                while (resultSet.hasNext()) {
                    RowRecord record = resultSet.next();
                    logger.trace(record.toString());
                    resultStrings.add(record.toString());
                }
            }
        } catch (IoTDBConnectionException e) {
            logger.error("Connection error iterating results", e);
        } catch (StatementExecutionException e) {
            logger.error("Execution error iterating results", e);
        }
    }

    @Override
    public List<String> getResultStrings() {
        return new LinkedList<>(resultStrings);
    }
}
