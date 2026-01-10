package pt.haslab.mulletbench.queries.queryResult;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class QueryResult {
    protected static final Logger logger = LogManager.getLogger();

    public QueryResult() {
        // Default constructor
    }

    public abstract int size();

    public abstract List<String> getResultStrings();
}
