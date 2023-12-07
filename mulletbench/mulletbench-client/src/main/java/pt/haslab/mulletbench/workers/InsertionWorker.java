package pt.haslab.mulletbench.workers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.haslab.mulletbench.TimeProvider;
import pt.haslab.mulletbench.datasets.Dataset;
import pt.haslab.mulletbench.datasets.DatasetReader;
import pt.haslab.mulletbench.OperationType;
import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.stats.Stats;
import pt.haslab.mulletbench.utils.ClientOptions;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class InsertionWorker extends Worker {

    private final boolean loopDataset;
    private final DatasetReader datasetReader;
    private final Integer batchSize;

    private final LinkedList<CompletableFuture<?>> futures;
    private final long sectionSize;

    private static final Logger logger = LogManager.getLogger();


    //TODO This should run periodically regardless of the batch size and write whatever has been read so far
    // with ScheduledThreadExecutor (if it allows dynamic scheduling)
    // otherwise wait/notifyall
    private void measuredWrite(List<String> measurements) {
        long before = TimeProvider.getNanoTime();
        // System.out.println("Worker " + workerNumber + " inserting " + measurements.size() + " measurements");
        if(connector.write(measurements, workerId)){
            long after = TimeProvider.getNanoTime();
            logger.debug("Time to write: " + (after - before / 1_000_000L) + " ms");
            stats.registerOperation(before, TimeProvider.getNanoTime(), measurements.size(), OperationType.INSERT);
        } else {
            logger.info("Failed insert");
            stats.registerOperation(before, TimeProvider.getNanoTime(), measurements.size(), OperationType.FAILED_INSERT);
        }

    }

    @Override
    public void run() {
        logger.info("Starting insertion worker");

        boolean finished = false;
        long inserted = 0;

        //stagger workers by waiting up to interval length
        stagger();

        while(!finished){

            // if pool has too many queued tasks hold adding more tasks
            if(!Worker.hold){
//                long before = System.nanoTime();

                List<String> measurements = datasetReader.read(this.batchSize, rateInterval);

//                long after = System.nanoTime();
//                logger.debug("Time to read " + (after - before)/1_000_000L + "ms");

                List<String> measurementsCopy = new LinkedList<>(measurements);

                futures.add(CompletableFuture.runAsync(() ->
                    measuredWrite(measurementsCopy)
                    ));

                inserted += measurements.size();
                measurements.clear();
            }

            try {
                Thread.sleep(rateInterval);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if(loopDataset && !datasetReader.hasNext()){
                datasetReader.reload();
            }

            if(!datasetReader.hasNext() || inserted >= sectionSize){
                finished = true;
            }
        }

        logger.info("Waiting for remaining operations");

        for(CompletableFuture<?> future : futures){
            future.join();
        }
    }

//    With shared dataset
    private InsertionWorker(int workerNumber, DatabaseConnector connector, String clientId, String dataFileName, String dataset, Integer batchSize, float rate, long workerSectionSize, Stats stats, boolean currentTime, Dataset sharedDataset) throws ClassNotFoundException {
        super(connector, stats, clientId, workerNumber);
        this.datasetReader = DatasetReader.getInstance(dataFileName, dataset, currentTime, sharedDataset, this.workerId);
        this.loopDataset = true;
        this.sectionSize = workerSectionSize;
        this.batchSize = batchSize;
        this.futures = new LinkedList<>();
        this.rateInterval = (int) (1000/rate);
    }

//    Without shared dataset
    private InsertionWorker(int workerNumber, DatabaseConnector connector, String clientId, String dataFileName, String dataset, Integer batchSize, float rate, long workerSectionSize, Stats stats, boolean currentTime) throws ClassNotFoundException {
        super(connector, stats, clientId, workerNumber);
        this.datasetReader = DatasetReader.getInstance(dataFileName, dataset, currentTime);
        this.loopDataset = true;
        this.sectionSize = workerSectionSize;
        this.batchSize = batchSize;
        this.futures = new LinkedList<>();
        this.rateInterval = (int) (1000/rate);
    }

    public InsertionWorker(DatabaseConnector connector, ClientOptions options, Stats stats, int workerNumber, boolean currentTime) throws ClassNotFoundException {
        this(workerNumber, connector, options.clientId, options.dataFile, options.dataset, options.insertion.batchSize, options.insertion.rate, options.insertion.workerSectionSize, stats, currentTime);
    }

    public InsertionWorker(DatabaseConnector connector, ClientOptions options, Stats stats, int workerNumber, boolean currentTime, Dataset sharedDataset) throws ClassNotFoundException {
        this(workerNumber, connector, options.clientId, options.dataFile, options.dataset, options.insertion.batchSize, options.insertion.rate, options.insertion.workerSectionSize, stats, currentTime, sharedDataset);
    }


}
