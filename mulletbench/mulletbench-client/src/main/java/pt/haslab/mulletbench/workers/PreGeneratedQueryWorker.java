package pt.haslab.mulletbench.workers;

import java.io.FileNotFoundException;
import java.util.List;

import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.stats.Stats;
import pt.haslab.mulletbench.utils.ClientOptions;

public class PreGeneratedQueryWorker extends QueryWorker {
    private final List<Query> queries;

    protected Query getQuery(int i){
        return queries.get(i);
    }

    private PreGeneratedQueryWorker(DatabaseConnector connector, String clientId, float rate, int count, boolean dumpQueries, Stats stats, int workerNumber, List<Query> queries) throws FileNotFoundException {
        super(connector, stats, clientId, workerNumber, rate, count, dumpQueries);
        this.queries = queries;
    }

    public PreGeneratedQueryWorker(DatabaseConnector connector, ClientOptions options, Stats stats, int workerNumber, List<Query> queries) throws FileNotFoundException {
        this(connector, options.clientId, options.query.rate, options.query.count, options.dumpQueries, stats, workerNumber, queries);
    }

}
