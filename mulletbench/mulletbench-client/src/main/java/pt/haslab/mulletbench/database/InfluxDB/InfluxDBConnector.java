package pt.haslab.mulletbench.database.InfluxDB;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.influxdb.client.*;
import com.influxdb.exceptions.InfluxException;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.WriteParameters;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;

import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.database.FailedQueryException;
import pt.haslab.mulletbench.queries.Query;
import pt.haslab.mulletbench.utils.InfluxDBOptions;
import pt.haslab.mulletbench.utils.InsertionOptions;


public class InfluxDBConnector implements DatabaseConnector {

    private final WriteParameters writeParameters;
    private final InfluxDBClient influxDBClient;
    private final QueryApi queryAPI;
    private final WriteApiBlocking writeAPIBlocking;

    private MeasurementFactory measurementFactory;

    private static final Logger logger = LogManager.getLogger();

    public boolean write(List<String> records, String deviceId){
        //TODO exceptions
        if(records.isEmpty()){
            logger.error("Measurement list length 0");
            return false;
        }

        long start = System.nanoTime();
        List<?> parsedRecords;
        parsedRecords = records.stream().map(line -> measurementFactory.getMeasurement(line, deviceId)).collect(Collectors.toList());

        long end = System.nanoTime();
        logger.debug("Time to parse: " + (end - start)/1000000);

        try{
            writeAPIBlocking.writeMeasurements(parsedRecords, writeParameters);
        } catch (InfluxException e){
            logger.error("Error inserting measurements", e);
            return false;
        }
        return true;
    }

    public List<String> query(Query query) throws FailedQueryException {
        try{
            List<FluxTable> results = queryAPI.query(query.queryString());

            List<String> resultStrings = new LinkedList<>();
            for(FluxTable fluxTable: results){
                List<FluxRecord> records = fluxTable.getRecords();
                for (final FluxRecord fluxRecord: records){
                    logger.trace(fluxRecord.getTime() + ": " + fluxRecord.getValueByKey("_value"));
                    resultStrings.add(fluxRecord.toString());
                }
            }

            return resultStrings;
        } catch (Exception e){
            logger.error("Exception while querying");
            throw new FailedQueryException(e.getMessage());
        }
    }

    public void close(){
        influxDBClient.close();
    }


    //TODO maybe builder pattern for setting parameters
    public InfluxDBConnector(String serverURL, char[] token, String orgID, String bucket, int writeTimeout, int readTimeout, String dataset) throws ClassNotFoundException {

        this.writeParameters = new WriteParameters(bucket, orgID, WritePrecision.NS);


        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .writeTimeout(writeTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS);

        InfluxDBClientOptions options = InfluxDBClientOptions.builder()
            .url(serverURL)
            .authenticateToken(token)
            .org(orgID)
            .okHttpClient(builder)
            .build();

        this.influxDBClient = InfluxDBClientFactory.create(options);
        this.queryAPI = influxDBClient.getQueryApi();

        this.writeAPIBlocking = influxDBClient.getWriteApiBlocking();

//       get class for measurements from dataset name
        try{
            this.measurementFactory = MeasurementFactory.getInstance(dataset);
        } catch (NoSuchMethodException e){
            logger.error("Invalid measurement class", e);
        }

    }

    public InfluxDBConnector(InfluxDBOptions options, InsertionOptions insertionOptions, String dataset) throws ClassNotFoundException {
        this(options.serverURL, options.token.toCharArray(), options.orgID, options.bucket, options.writeTimeout, options.readTimeout, dataset);
    }
}
