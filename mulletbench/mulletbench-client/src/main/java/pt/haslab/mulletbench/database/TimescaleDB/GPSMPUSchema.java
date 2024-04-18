package pt.haslab.mulletbench.database.TimescaleDB;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

record SchemaSensor(String name, Integer id){

}

public class GPSMPUSchema implements TimescaleDBSchema{
    private final List<SchemaSensor> schemas;

    private static final Logger logger = LogManager.getLogger();


    public String getName(){
        return "GPSMPU";
    }

    public GPSMPUSchema(){
        List<String> sensorList = new ArrayList<>();
        sensorList.add("acc_x_dashboard");
        sensorList.add("acc_y_dashboard");
        sensorList.add("acc_z_dashboard");
        sensorList.add("acc_x_above_suspension");
        sensorList.add("acc_y_above_suspension");
        sensorList.add("acc_z_above_suspension");
        sensorList.add("acc_x_below_suspension");
        sensorList.add("acc_y_below_suspension");
        sensorList.add("acc_z_below_suspension");
        sensorList.add("gyro_x_dashboard");
        sensorList.add("gyro_y_dashboard");
        sensorList.add("gyro_z_dashboard");
        sensorList.add("gyro_x_above_suspension");
        sensorList.add("gyro_y_above_suspension");
        sensorList.add("gyro_z_above_suspension");
        sensorList.add("gyro_x_below_suspension");
        sensorList.add("gyro_y_below_suspension");
        sensorList.add("gyro_z_below_suspension");
        sensorList.add("mag_x_dashboard");
        sensorList.add("mag_y_dashboard");
        sensorList.add("mag_z_dashboard");
        sensorList.add("mag_x_above_suspension");
        sensorList.add("mag_y_above_suspension");
        sensorList.add("mag_z_above_suspension");
        sensorList.add("temp_dashboard");
        sensorList.add("temp_above_suspension");
        sensorList.add("temp_below_suspension");
        sensorList.add("latitude");
        sensorList.add("longitude");
        sensorList.add("speed");

        this.schemas = new ArrayList<>();
        IntStream.range(0, sensorList.size()).forEach(i -> this.schemas.add(new SchemaSensor(sensorList.get(i),i)));
    }

    @Override
    public void parse(PreparedStatement insertStatement, String record, String deviceId) throws SQLException {
        String[] splitString = record.split(",");

        String[] splitTimestamp = splitString[0].split("\\.");
        long timestampSeconds = Long.parseLong(splitTimestamp[0]);
        long timestampDecimals = Long.parseLong(splitTimestamp[1]);

        Instant instant =  Instant.ofEpochSecond(timestampSeconds).plus(timestampDecimals,ChronoUnit.NANOS);
//        long timestampNano = (long) (timestampDecimals * (100_000_000 / Math.pow(10, splitTimestamp[1].length()-1)));

        for (int s = 1; s < 32; s++) {
//          Skip unwanted variable
            if(s == 28)
                continue;
            insertStatement.setTimestamp(1, Timestamp.from(instant));
//            insertStatement.setLong(1, (timestampSeconds * 1_000_000_000) + timestampNano);
            insertStatement.setString(2, deviceId);
            if(s < 28){
                insertStatement.setInt(3, s-1);
            } else if (s > 28) {
                insertStatement.setInt(3, s-2);
            }
            insertStatement.setDouble(4, Double.parseDouble(splitString[s]));
            logger.debug("Insert " + splitString[s] + " into " + deviceId);
            insertStatement.addBatch();
        }
    }

    public boolean addSensors(Connection connection){
        try{
            PreparedStatement insertSensorStatement = connection.prepareStatement("INSERT INTO sensors (id, name) values (?,?)");
            this.schemas.forEach(sensor -> {
                try {
                    insertSensorStatement.setInt(1,sensor.id());
                    insertSensorStatement.setString(2, sensor.name());
                    insertSensorStatement.execute();
                } catch (SQLException e) {
                    logger.warn("Failed to add sensor");
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e){
            return false;
        }
        return true;
    }

    @Override
    public List<String> getSchemas() {
        return this.schemas.stream().map(SchemaSensor::name).toList();
    }

}
