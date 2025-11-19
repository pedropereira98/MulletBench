package pt.haslab.mulletbench;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.database.DatabaseConnectionFailedException;
import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.database.DatabaseConnectorFactory;
import pt.haslab.mulletbench.database.FailedQueryException;
import pt.haslab.mulletbench.datasets.Dataset;
import pt.haslab.mulletbench.datasets.SharedDataset;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.queries.queryBuilders.QueryBuilder;
import pt.haslab.mulletbench.queries.queryGenerators.FloatsQueryGenerator;
import pt.haslab.mulletbench.queries.queryGenerators.QueryGenerator;
import pt.haslab.mulletbench.queries.queryGenerators.datasetProcessors.DatasetProcessor;
import pt.haslab.mulletbench.queries.queryGenerators.datasetProcessors.FloatsDatasetProcessor;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.CurrentTimeController;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.DatasetTimeController;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;
import pt.haslab.mulletbench.stats.StatsCollector;
import pt.haslab.mulletbench.utils.ClientOptions;
import pt.haslab.mulletbench.workers.InsertionWorker;
import pt.haslab.mulletbench.workers.PoolManagementWorker;
import pt.haslab.mulletbench.workers.PreGeneratedQueryWorker;
import pt.haslab.mulletbench.workers.RealTimeQueryWorker;
import pt.haslab.mulletbench.workers.Worker;

public class BenchmarkClient {

    private static final Logger logger = LogManager.getLogger();

    private final StatsCollector statsCollector;
    private final ClientOptions options;

    private final DatabaseConnectorFactory databaseConnectorFactory; // for connection per worker

    private Socket orchestratorSocket;
    private ObjectOutputStream objOut;
    private ObjectInputStream objIn;

    public BenchmarkClient(ClientOptions options) {
        this.options = options;
        this.statsCollector = new StatsCollector(options.type, options.numWorkers);
        this.databaseConnectorFactory = new DatabaseConnectorFactory(options);
    }

    private boolean waitForStart() {
        try {
            logger.debug("Waiting for orchestrator start message");

            SyncMessage receivedMessage = (SyncMessage) objIn.readObject();

            logger.debug(receivedMessage);

            return receivedMessage.equals(SyncMessage.START);
        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Error in connection to orchestrator. Aborting");
            logger.error(e);
            return false;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            logger.error("Invalid message received. Aborting");
            return false;
        }
    }

    private void send(SyncMessage message) {
        try {
            objOut.writeObject(message);
        } catch (IOException e) {
            logger.error("Failed to send " + message.toString() + " message to orchestrator");
            e.printStackTrace();
        }
    }

    private void connect() throws IOException {
        this.orchestratorSocket = new Socket(InetAddress.getByName(options.orchestratorAddress), options.orchestratorPort);
        logger.debug("Connected to orchestrator " + orchestratorSocket.getInetAddress().toString() + " " + orchestratorSocket.getPort());

        this.objOut = new ObjectOutputStream(new BufferedOutputStream(orchestratorSocket.getOutputStream())); // better for larger writes
        this.objOut.writeObject(options.clientId + ";" + options.clientAddress);
        this.objOut.flush();
        this.objIn = new ObjectInputStream(orchestratorSocket.getInputStream());
        logger.debug("Wrote object with " + options.clientId);
    }

    public void insertionWorkload() throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException, InterruptedException {
        logger.info("Starting insertion workload");

        Thread[] threadList = new Thread[options.numWorkers];

        try {
            Dataset sharedDataset = null;
            if (options.sharedDataset) {
                sharedDataset = new SharedDataset(options.dataFile);
            }

            DatabaseConnector databaseConnector = null;
            if (options.sharedConnection) {
                databaseConnector = databaseConnectorFactory.getInstance();
            }

            for (int i = 0; i < options.numWorkers; i++) {
                if (!options.sharedConnection) {
                    databaseConnector = databaseConnectorFactory.getInstance();
                }
                if (options.sharedDataset) {
                    threadList[i] = new Thread(new InsertionWorker(databaseConnector, options, statsCollector.getStats(i), i, options.currentTime, sharedDataset));
                } else {
                    threadList[i] = new Thread(new InsertionWorker(databaseConnector, options, statsCollector.getStats(i), i, options.currentTime));
                }
            }

            connect();

            if (!waitForStart()) {
                // could throw exception
                logger.error("Waiting for orchestrator start instruction failed. Aborting");
                return;
            }

            Thread poolManager = new Thread(new PoolManagementWorker(options.insertion.rate));
            poolManager.start();
            this.statsCollector.startCollection();

            for (int i = 0; i < options.numWorkers; i++) {
                threadList[i].start();
            }

            logger.info("Waiting for workers to finish...");
            for (int i = 0; i < options.numWorkers; i++) {
                threadList[i].join();
            }

            poolManager.interrupt();

        } catch (DatabaseConnectionFailedException e) {
            throw new RuntimeException(e);
        }
    }

    private Instant parseQueryResult(String value) {
        if (options.target.toLowerCase().equals("iotdb")) {
            String[] parts = value.split("( |\t)");
            String timestamp = parts[0];
            long nanoTs = Long.parseLong(timestamp);
            Instant instant = Instant.ofEpochSecond(nanoTs / 1_000_000_000L, nanoTs % 1_000_000_000L);
            return instant;
        } 

        String[] parts = value.split(" ");

        String timestamp = null;
        for (String part : parts) {
            if (part.startsWith("_time=")) {
                timestamp = part.substring(6); // remove "_time="
                break;
            }
        }

        return Instant.parse(timestamp.replace("Z", "+00:00"));
    }

    private Instant[] getQueryRange(DatabaseConnector databaseConnector, DatasetProcessor datasetProcessor) {
        Instant[] range = new Instant[2];
        if (options.currentTime)
            return new Instant[]{Instant.now(), Instant.now()};

        QueryGenerator queryGenerator = new FloatsQueryGenerator(QueryBuilder.createQueryBuilder(options), options, new CurrentTimeController(), (FloatsDatasetProcessor) datasetProcessor);

        Query firstRecordQuery = queryGenerator.getFirstRecordQuery();
        Query lastRecordQuery = queryGenerator.getLastRecordQuery();

        List<String> firstRecordResult = null;
        List<String> lastRecordResult = null;

        try {
            firstRecordResult = databaseConnector.query(firstRecordQuery.queryString());
            lastRecordResult = databaseConnector.query(lastRecordQuery.queryString());
        } catch (FailedQueryException e) {
            logger.error("Failed to get start and end range for dataset", e);
            return null;
        }

        if (firstRecordResult.isEmpty() || lastRecordResult.isEmpty()) {
            logger.error("No results found for first or last record queries. Aborting");
            return null;
        }

        
        String firstRecord = firstRecordResult.get(0);
        String lastRecord = lastRecordResult.get(0);

        System.out.println("First record: " + firstRecord);
        System.out.println("Last record: " + lastRecord);

        range[0] = parseQueryResult(firstRecord);
        range[1] = parseQueryResult(lastRecord);
        
        return range;
    }

    public void queryWorkload() throws DatabaseConnectionFailedException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        logger.info("Starting query workload");
        TimeController tc;
        if (options.currentTime) {
            tc = new CurrentTimeController();
        } else {
            tc = new DatasetTimeController(options.query.numberOfLoops);
        }

        DatasetProcessor datasetProcessor;
        try {
            datasetProcessor = DatasetProcessor.getInstance(options.dataset, tc);
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        try {
            // Data processing phase
            // Collecting values for queries
            datasetProcessor.process("/data/" + options.dataFile);
            logger.info("Finished data processing phase");
        } catch (IOException e) {
            logger.error("Failed to process data file", e);
            return;
        }

        Thread[] threadList = new Thread[options.numWorkers];

        DatabaseConnector databaseConnector = null;
        if (options.sharedConnection) {
            databaseConnector = databaseConnectorFactory.getInstance();
        }

        try {

            if (!options.currentTime) {
                Instant[] queryRange = getQueryRange(databaseConnectorFactory.getInstance(), datasetProcessor);
                tc.setStart(queryRange[0]);
                tc.setEnd(queryRange[1]);
            }

            for (int i = 0; i < options.numWorkers; i++) {
                if (!options.sharedConnection) {
                    databaseConnector = databaseConnectorFactory.getInstance();
                }

                // Generate queries before executing worker
                if (options.currentTime) {
                    QueryGenerator queryGenerator = new FloatsQueryGenerator(QueryBuilder.createQueryBuilder(options), options, tc, (FloatsDatasetProcessor) datasetProcessor);
                    statsCollector.setQuerySeed(queryGenerator.getCurrentSeed());
                    queryGenerator.setStart(System.currentTimeMillis());
                    threadList[i] = new Thread(new RealTimeQueryWorker(databaseConnector, options, statsCollector.getStats(i), queryGenerator, i));
                } else {
                    QueryGenerator queryGenerator = new FloatsQueryGenerator(QueryBuilder.createQueryBuilder(options), options, tc, (FloatsDatasetProcessor) datasetProcessor);
                    queryGenerator.incrementSeed(i);
                    statsCollector.setQuerySeed(queryGenerator.getCurrentSeed());
                    List<Query> workerQueries = queryGenerator.generateQueries(options.query.count);
                    threadList[i] = new Thread(new PreGeneratedQueryWorker(databaseConnector, options, statsCollector.getStats(i), i, workerQueries));
                }
            }

            connect();

            if (!waitForStart()) {
                // could throw exception
                logger.error("Waiting for orchestrator start instruction failed. Aborting");
                return;
            }

            this.statsCollector.startCollection();

            for (int i = 0; i < options.numWorkers; i++) {
                logger.info("Starting " + PreGeneratedQueryWorker.class.getSimpleName() + " " + i);
                threadList[i].start();
            }

            logger.info("Waiting for workers to finish...");
            for (int i = 0; i < options.numWorkers; i++) {
                threadList[i].join();
            }

        } catch (Exception e) {
            logger.error("Failed to create worker", e);
        }
    }

    public void run() {
        logger.info("Waiting for orchestrator start message");

        Worker.setNumWorkers(options.numWorkers);
        try {
            switch (options.type) {
                case INSERT ->
                    insertionWorkload();
                case QUERY ->
                    queryWorkload();
                default -> {
                    logger.error("Invalid worker type");
                    return;
                }
            }
        } catch (Exception e) {
            logger.error("Workload execution failed", e);
        } catch (DatabaseConnectionFailedException e) {
            logger.error("Workload execution failed");
            throw new RuntimeException(e);
        }

        this.statsCollector.endCollection();

        logger.info("Finished workload");
        this.statsCollector.printStats(0);

        logger.debug("Sending collected statistics to orchestrator");

        try {
            this.orchestratorSocket.setTcpNoDelay(true); //better for bigger writes
            this.objOut.writeObject(this.statsCollector);
            this.objOut.flush();

            logger.debug("Sent statistics to orchestrator");
        } catch (IOException e) {
            logger.error("Failed to send results to orchestrator");
            e.printStackTrace();
        }

        try {
            // Thread.sleep(10000); 

            this.orchestratorSocket.shutdownOutput();

            logger.info("Shutdown output stream to orchestrator");

            this.objOut.close();
            this.objIn.close();
            this.orchestratorSocket.close();
            logger.info("Connection to orchestrator closed");
        } catch (IOException e) {
            logger.error("Failed to close orchestrator socket");
            e.printStackTrace();
        }
    }
}
