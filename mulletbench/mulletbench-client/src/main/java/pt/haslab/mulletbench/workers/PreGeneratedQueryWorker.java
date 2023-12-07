package pt.haslab.mulletbench.workers;

import java.util.List;

import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.utils.ClientOptions;
import pt.haslab.mulletbench.stats.Stats;

public class PreGeneratedQueryWorker extends QueryWorker {
    private final List<Query> queries;

    protected Query getQuery(int i){
        return queries.get(i);
    }

    private PreGeneratedQueryWorker(DatabaseConnector connector, String clientId, float rate, int count, Stats stats, int workerNumber, List<Query> queries) {
        super(connector, stats, clientId, workerNumber, rate, count);
        this.queries = queries;
    }

    public PreGeneratedQueryWorker(DatabaseConnector connector, ClientOptions options, Stats stats, int workerNumber, List<Query> queries) {
        this(connector, options.clientId, options.query.rate, options.query.count, stats, workerNumber, queries);
    }

}
