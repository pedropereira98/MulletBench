package pt.haslab.mulletbench;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.database.DatabaseConnectionFailedException;
import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.database.DatabaseConnectorFactory;
import pt.haslab.mulletbench.datasets.Dataset;
import pt.haslab.mulletbench.datasets.SharedDataset;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.queries.queryBuilders.QueryBuilder;
import pt.haslab.mulletbench.queries.queryGenerators.*;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.CurrentTimeController;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.DatasetTimeController;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;
import pt.haslab.mulletbench.stats.StatsCollector;
import pt.haslab.mulletbench.utils.ClientOptions;
import pt.haslab.mulletbench.workers.*;

public class BenchmarkClient {

    private static final Logger logger = LogManager.getLogger();

    private final StatsCollector statsCollector;
    private final ClientOptions options;

    private final DatabaseConnectorFactory databaseConnectorFactory; // for connection per worker

    private Socket orchestratorSocket;
    private ObjectOutputStream objOut;
    private ObjectInputStream objIn;

    public BenchmarkClient(ClientOptions options)  {
        this.options = options;
        this.statsCollector = new StatsCollector(options.type, options.numWorkers);
        this.databaseConnectorFactory = new DatabaseConnectorFactory(options);
    }

    private boolean waitForStart(){
        try{
            logger.debug("Waiting for orchestrator start message");

            SyncMessage receivedMessage = (SyncMessage) objIn.readObject();

            logger.debug(receivedMessage);


            return receivedMessage.equals(SyncMessage.START);
        } catch (IOException e){
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

    private void send(SyncMessage message){
        try {
            objOut.writeObject(message);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            logger.error("Failed to send " + message.toString() + " message to orchestrator");
            e.printStackTrace();
        }
    }

    private void connect() throws IOException{
        this.orchestratorSocket = new Socket(InetAddress.getByName(options.orchestratorAddress), options.orchestratorPort);
        logger.debug("Connected to orchestrator " + orchestratorSocket.getInetAddress().toString() + " " + orchestratorSocket.getPort());

        this.objOut = new ObjectOutputStream(new BufferedOutputStream(orchestratorSocket.getOutputStream())); // better for larger writes
        this.objOut.writeObject(options.clientId);
        this.objOut.flush();
        this.objIn = new ObjectInputStream(orchestratorSocket.getInputStream());
        logger.debug("Wrote object with " + options.clientId);
    }

    public void insertionWorkload() throws ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException, InterruptedException {
        logger.info("Starting insertion workload");

        Thread[] threadList = new Thread[options.numWorkers];

        try {
            Dataset sharedDataset = null;
            if(options.sharedDataset){
                sharedDataset = new SharedDataset(options.dataFile);
            }

            DatabaseConnector databaseConnector = null;
            if(options.sharedConnection){
                databaseConnector = databaseConnectorFactory.getInstance();
            }

            for(int i = 0; i < options.numWorkers; i++){
                if(!options.sharedConnection){
                    databaseConnector = databaseConnectorFactory.getInstance();
                }
                if(options.sharedDataset){
                    threadList[i] = new Thread(new InsertionWorker(databaseConnector, options, statsCollector.getStats(i), i, options.currentTime, sharedDataset));
                } else {
                    threadList[i] = new Thread(new InsertionWorker(databaseConnector, options, statsCollector.getStats(i), i, options.currentTime));
                }
            }

            connect();

            if(!waitForStart()){
                // could throw exception
                logger.error("Waiting for orchestrator start instruction failed. Aborting");
                return;
            }

            Thread poolManager = new Thread(new PoolManagementWorker(options.insertion.rate));
            poolManager.start();
            this.statsCollector.startCollection();

            for(int i = 0; i < options.numWorkers; i++){
                threadList[i].start();
            }

            logger.info("Waiting for workers to finish...");
            for(int i = 0; i < options.numWorkers; i++){
                threadList[i].join();
            }

            poolManager.interrupt();

        } catch (DatabaseConnectionFailedException e) {
            throw new RuntimeException(e);
        }
    }

    public void queryWorkload() throws DatabaseConnectionFailedException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        logger.info("Starting query workload");
        TimeController tc;
        if (options.currentTime){
            tc = new CurrentTimeController();
        } else {
            tc = new DatasetTimeController(options.query.numberOfLoops);
        }

        QueryGenerator queryGenerator;
        try {
            queryGenerator = QueryGenerator.getInstance(options.dataset, QueryBuilder.createQueryBuilder(options), options, tc);
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        try{
            // Data processing phase
            // Collecting values for queries
            queryGenerator.process(options.dataFile);
            logger.info("Finished data processing phase");
        } catch (Exception e) {
            logger.error("Failed to process data file", e);
            return;
        }

        Thread[] threadList = new Thread[options.numWorkers];

        DatabaseConnector databaseConnector = null;
        if(options.sharedConnection){
            databaseConnector = databaseConnectorFactory.getInstance();
        }

        try {
            for(int i = 0; i < options.numWorkers; i++){
                if(!options.sharedConnection){
                    databaseConnector = databaseConnectorFactory.getInstance();
                }

                // Generate queries before executing worker
                if(options.currentTime){
                    queryGenerator.setStart(System.currentTimeMillis());
                    threadList[i] = new Thread(new RealTimeQueryWorker(databaseConnector, options, statsCollector.getStats(i), queryGenerator, i));
                } else{
                    List<Query> workerQueries = queryGenerator.generateQueries(options.query.count);
                    threadList[i] = new Thread(new PreGeneratedQueryWorker(databaseConnector, options, statsCollector.getStats(i), i, workerQueries));
                }
            }

            connect();

            if(!waitForStart()){
                // could throw exception
                logger.error("Waiting for orchestrator start instruction failed. Aborting");
                return;
            }

            this.statsCollector.startCollection();

            for(int i = 0; i < options.numWorkers; i++){
                logger.info("Starting " + PreGeneratedQueryWorker.class.getSimpleName() + " " + i);
                threadList[i].start();
            }

            logger.info("Waiting for workers to finish...");
            for(int i = 0; i < options.numWorkers; i++){
                threadList[i].join();
            }

        } catch (Exception e) {
            logger.error("Failed to create worker", e);
        }
    }

    public void run() {
        logger.info("Waiting for orchestrator start message");

        Worker.setNumWorkers(options.numWorkers);
        try{
            switch (options.type) {
                case INSERT -> insertionWorkload();
                case QUERY -> queryWorkload();
                default -> {
                    logger.error("Invalid worker type");
                    return;
                }
            }
        } catch (Exception e){
            logger.error("Workload execution failed", e);
        } catch (DatabaseConnectionFailedException e) {
            logger.error("Workload execution failed");
            throw new RuntimeException(e);
        }

        this.statsCollector.endCollection();

        logger.info("Finished workload");
        this.statsCollector.printStats();

        logger.debug("Sending collected statistics to orchestrator");
        try{
            this.orchestratorSocket.setTcpNoDelay(true); //better for bigger writes
            this.objOut.writeObject(this.statsCollector);
            this.objOut.flush();
            logger.info("Statistics successfully sent");
        } catch (IOException e){
            logger.error("Failed to send results to orchestrator");
            e.printStackTrace();
        }

        try{
            this.objOut.close();
            this.objIn.close();
            this.orchestratorSocket.close();
            logger.info("Connection to orchestrator closed");
        } catch (IOException e){
            logger.error("Failed to close orchestrator socket");
            e.printStackTrace();
        }
    }
}
