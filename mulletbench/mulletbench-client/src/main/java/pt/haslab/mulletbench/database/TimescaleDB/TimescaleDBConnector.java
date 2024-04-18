package pt.haslab.mulletbench.database.TimescaleDB;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.haslab.mulletbench.database.DatabaseConnectionFailedException;
import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.database.FailedQueryException;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.utils.InsertionOptions;
import pt.haslab.mulletbench.utils.TimescaleDBOptions;

import java.lang.reflect.InvocationTargetException;
import java.sql.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TimescaleDBConnector implements DatabaseConnector {

    private static final Logger logger = LogManager.getLogger();
    private Connection connection;

    private final TimescaleDBSchema schema;
    private final String insertStatementString;

    private PreparedStatement insertStatement;

    private final String connUrl;
    private final String tableName;

    private Map<String, Integer> deviceIDs;

    @Override
    public boolean write(List<String> records, String deviceId) {
        try{
            long start = System.nanoTime();
            Connection conn = DriverManager.getConnection(connUrl);
            long end = System.nanoTime();
            logger.info("Time to connect: " + (end - start)/1_000_000);

            start = System.nanoTime();
            PreparedStatement insertStatmnt = conn.prepareStatement(insertStatementString);
            for(String record: records){
                schema.parse(insertStatmnt, record, deviceId);
            }
            end = System.nanoTime();
            logger.info("Time to parse: " + (end - start)/1_000_000);
    //      TODO If all workers share connector, this doesn't work ??
            logger.info("Executing batch");
            insertStatmnt.executeBatch();
//          conn.commit();
            logger.info("Batch executed");

//            insertStatmnt.close();
            conn.close();
        } catch (SQLException e){
            logger.error("Insertion statement failed", e);
            return false;
        }

        return true;
    }

    @Override
    public List query(Query query) throws FailedQueryException {
// TODO
        List<String> results = new LinkedList<>();
        try{
            try (ResultSet rs = connection.createStatement().executeQuery(query.queryString())) {
                while (rs.next()) {
                    try {
                        results.add(String.format("%s, %f, %s, %d %n", rs.getTimestamp(1), rs.getDouble(2), rs.getString(3), rs.getInt(4)));
                    } catch (SQLException e) {
                        results.add(String.format("%f, %s, %d %n", rs.getDouble(1), rs.getString(2), rs.getInt(3)));
                    }
                }
                logger.debug(results);
            }
        } catch (SQLException e){
            throw new FailedQueryException(e.getMessage());
        }

        return results;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            logger.error("Error closing connection", e);
        }
    }
    public TimescaleDBConnector(String host, String port, String name, String username, String password, String tableName, String dataset) throws DatabaseConnectionFailedException, ClassNotFoundException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, SQLException {
        //connect to database
        this.schema = TimescaleDBSchema.getInstance(dataset);
        this.connUrl = String.format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s",host,port,name,username,password);
        this.tableName = tableName;
        this.insertStatementString = String.format("INSERT INTO %s (time, device_id, sensor_id, value) values (?, ?, ?, ?)", tableName);

        int retries = 0;
        boolean connected = false;
        while(retries < 4 && !connected) {
            try {
                this.connection = DriverManager.getConnection(connUrl);
                connected = true;
                System.out.println(connection.getClientInfo());
            } catch (SQLException e) {
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


        // Add sensors to sensor table

        int numSensors = 0;
//      query sensor table
        try (ResultSet rs = connection.createStatement().executeQuery("SELECT * FROM sensors;")) {
            while (rs.next()) {
                numSensors++;
            }
        }

//      if there are none, insert
        if(numSensors == 0){
            schema.addSensors(connection);
        }

        this.insertStatement = connection.prepareStatement("INSERT INTO sensor_data (time, device_id, sensor_id, value) values (?, ?, ?, ?)");

        connection.setAutoCommit(false);
    }

    public TimescaleDBConnector(TimescaleDBOptions options, InsertionOptions insertionOptions, String dataset) throws ClassNotFoundException, DatabaseConnectionFailedException, SQLException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        this(options.host,options.port,options.database, options.username,options.password, options.tableName, dataset);
    }
}
