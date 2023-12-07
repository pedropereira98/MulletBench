package pt.haslab.mulletbench.workers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.haslab.mulletbench.OperationType;
import pt.haslab.mulletbench.TimeProvider;
import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.database.FailedQueryException;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.stats.Stats;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class QueryWorker extends Worker {

    private final int count;

    private final LinkedList<CompletableFuture<?>> futures;

    private static final Logger logger = LogManager.getLogger();

    private void measuredQuery(Query query){
        long before = TimeProvider.getNanoTime();
        logger.info("Starting query");
        logger.debug(query);
        try {
            List<String> results = connector.query(query.queryString());
            long after = TimeProvider.getNanoTime();
            // TODO check results?

            stats.registerOperation(before, after, results.size(), query.type());
        } catch (FailedQueryException e){
            long after = TimeProvider.getNanoTime();
            logger.error(e.getMessage());
            OperationType failedType = switch (query.type()){
                case AGGREGATION -> OperationType.FAILED_AGGREGATION;
                case FILTER -> OperationType.FAILED_FILTER;
                case DOWNSAMPLING -> OperationType.FAILED_DOWNSAMPLING;
                case OUTLIER_FILTER -> OperationType.FAILED_OUTLIER_FILTER;
                default -> throw new IllegalStateException("Unexpected value: " + query.type());
            };
            stats.registerOperation(before, after, 0, failedType);
        }
    }

    protected abstract Query getQuery(int i);

    @Override
    public void run() {
        logger.info("Starting real-time query worker");

        stagger();

        // Query de agregação (média) por hora, dia, mes
        // Query de downsampling (média) por minuto, hora, dia, mes
        // query de filtragem (outliers)

        logger.debug("Doing " + this.count + " queries with rate " + this.rateInterval + "ms");
        int i = 0;
        while(i < this.count){
            // if pool has too many queued tasks stop adding more tasks
            if(!Worker.hold){
                Query query = getQuery(i++);
                logger.info("Starting query");
                futures.add(CompletableFuture.runAsync(() ->
                    measuredQuery(query)
                ));
            }

            try{
                Thread.sleep(this.rateInterval);
            } catch (InterruptedException e) {
                logger.error("Error while sleeping", e);
            }
        }

        for(CompletableFuture<?> future : futures){
            future.join();
        }
    }

    public QueryWorker(DatabaseConnector connector, Stats stats, String clientId, int workerNumber, float rate, int count) {
        super(connector, stats, clientId, workerNumber);
        this.futures = new LinkedList<>();
        this.rateInterval = (int) (1000 / rate);
        this.count = count;
    }
}
