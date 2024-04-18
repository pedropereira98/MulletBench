package pt.haslab.mulletbench.database.IoTDB;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.Session;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.iotdb.tsfile.write.record.Tablet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.database.DatabaseConnectionFailedException;
import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.database.FailedQueryException;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.utils.InsertionOptions;
import pt.haslab.mulletbench.utils.IoTDBOptions;

public class IoTDBConnector implements DatabaseConnector {
    private final Session session;

    private final int batchSize;
    private final IoTDBSchema schema; //TODO constructor gets it from options

    private final String devicePath;

    private static final Logger logger = LogManager.getLogger();

    @Override
    public void close() {
        try{
            session.close();
        } catch (IoTDBConnectionException e){
            logger.error("Error closing connection", e);
        }
    }

    @Override
    public List query(Query query) throws FailedQueryException {
        SessionDataSet resultSet;

        try{
            resultSet = session.executeQueryStatement(query.queryString());
        } catch (IoTDBConnectionException e){
            logger.error("Connection error while querying", e);
            throw new FailedQueryException(e.getMessage());
        } catch (StatementExecutionException e){
            logger.error("Query execution failed", e);
            throw new FailedQueryException(e.getMessage());
        }

        List<String> results = new LinkedList<>();
        try{
            if(resultSet != null){
                while(resultSet.hasNext()){
                    RowRecord record = resultSet.next();
                    logger.trace(record.toString());
                    results.add(record.toString());
                }
            }
        } catch (IoTDBConnectionException e){
            logger.error("Connection error iterating results", e);
        } catch (StatementExecutionException e){
            logger.error("Execution error iterating results", e);
        }

        return results;
    }

    private void insertTablet(Tablet tablet) throws StatementExecutionException{
        try{
            session.insertTablet(tablet);
            tablet.reset();
        } catch (IoTDBConnectionException e){
            logger.error("Connection error inserting tablet", e);
        }
    }

    private Tablet parseTablet(List<String> records, String deviceId) throws IllegalArgumentException{
        if(records.size() > this.batchSize){
            logger.error("Records size is greater than batch size (" + records.size() + " > " + this.batchSize + ")");
            throw new IllegalArgumentException("Records size is greater than batch size");
        }

        Tablet tablet = new Tablet(devicePath.replace("*", deviceId), schema.getSchemas(), this.batchSize);

        // System.out.println("Tablet - " + tablet.deviceId + " " + tablet.getSchemas() + " " + tablet.getMaxRowNumber());

        for(String r: records){
            int rowIndex = tablet.rowSize++;

            tablet = schema.parse(tablet, rowIndex, r);
        }

        return tablet;
    }

    @Override
    public boolean write(List<String> records, String deviceId) {
        // insertTablet is recommended to improve write efficiency
        long beforep = System.nanoTime();
        Tablet t = parseTablet(records, deviceId);
        logger.debug("Time to parse: " + (System.nanoTime() - beforep)/1000000 + "ms");
        try{
            insertTablet(t);
        } catch (StatementExecutionException e){
            logger.error("Execution error inserting tablet", e);
            return false;
        }
        // TODO flush to cloud node using http?
        return true;
    }

    public IoTDBConnector(String host, String port, String username, String password, String devicePath, int batchSize, String dataset) throws DatabaseConnectionFailedException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        this.session = new Session.Builder().host(host).port(Integer.parseInt(port)).username(username).password(password).thriftDefaultBufferSize(20480).build(); //increases default buffer size to reduce initial buffer resizing
        this.devicePath = devicePath;
        this.batchSize = batchSize;

        this.schema = IoTDBSchema.getInstance(dataset);

        int retries = 0;
        boolean connected = false;
        while(retries < 4 && !connected) {
            try {
                session.open();
                connected = true;
            } catch (IoTDBConnectionException e) {
                logger.error(e.getMessage());
                try {
                    Thread.sleep(15 * 1000); // sleep 15 seconds waiting for DB to be ready
                    retries += 1;
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        if(!connected){
            throw new DatabaseConnectionFailedException("Too many connection retries");
        }

    }

    public IoTDBConnector(IoTDBOptions options, InsertionOptions insertionOptions, String dataset) throws DatabaseConnectionFailedException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        this(options.host, options.port, options.username, options.password, options.devicePath, insertionOptions.batchSize, dataset);
    }
}
