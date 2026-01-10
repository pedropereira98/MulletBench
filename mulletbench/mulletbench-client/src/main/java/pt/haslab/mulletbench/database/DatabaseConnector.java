package pt.haslab.mulletbench.database;

import java.util.List;

import pt.haslab.mulletbench.queries.queryResult.QueryResult;

public interface DatabaseConnector {

    //TODO use generics to check type in implementations
    //Performs insertion of a batch of records for a deviceId
    boolean write(List<String> records, String deviceId);

    QueryResult query(String query) throws FailedQueryException;

    void close();

}
