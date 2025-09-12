package pt.haslab.mulletbench.workers;

import java.io.FileNotFoundException;

import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.queries.queryGenerators.QueryGenerator;
import pt.haslab.mulletbench.stats.Stats;
import pt.haslab.mulletbench.utils.ClientOptions;

public class RealTimeQueryWorker extends QueryWorker {

    private final QueryGenerator queryGenerator;

    protected Query getQuery(int i){
        return queryGenerator.generateQuery();
    }

    private RealTimeQueryWorker(DatabaseConnector connector, String clientId, float rate, int count, boolean dumpQueries, Stats stats, QueryGenerator queryGenerator, int workerNumber) throws FileNotFoundException {
        super(connector, stats, clientId, workerNumber, rate, count, dumpQueries);
        this.queryGenerator = queryGenerator;
        this.queryGenerator.incrementSeed(workerNumber);
    }

    public RealTimeQueryWorker(DatabaseConnector connector, ClientOptions options, Stats stats, QueryGenerator queryGenerator, int workerNumber) throws FileNotFoundException {
        this(connector, options.clientId, options.query.rate, options.query.count, options.dumpQueries, stats, queryGenerator, workerNumber);
    }
}
