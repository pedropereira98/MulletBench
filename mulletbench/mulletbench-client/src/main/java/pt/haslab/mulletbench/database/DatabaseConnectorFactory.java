package pt.haslab.mulletbench.database;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.haslab.mulletbench.database.InfluxDB.InfluxDBConnector;
import pt.haslab.mulletbench.database.IoTDB.IoTDBConnector;
import pt.haslab.mulletbench.database.TimescaleDB.TimescaleDBConnector;
import pt.haslab.mulletbench.utils.ClientOptions;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

public class DatabaseConnectorFactory {
    private static final Logger logger = LogManager.getLogger();


    ClientOptions options;
    public DatabaseConnectorFactory(ClientOptions options){
        this.options = options;
    }

   public DatabaseConnector getInstance() throws ClassNotFoundException, DatabaseConnectionFailedException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, SQLException {
        return switch (options.target) {
            case "influx":
                yield new InfluxDBConnector(options.influx, options.insertion, options.dataset);
            case "iotdb":
                yield new IoTDBConnector(options.iotdb, options.insertion, options.dataset);
            case "timescale":
                yield new TimescaleDBConnector(options.timescale, options.insertion, options.dataset);
            default: {
                logger.error("Invalid target database");
                throw new IllegalArgumentException("Unknown database " + options.target);
            }
        };
    }
}
