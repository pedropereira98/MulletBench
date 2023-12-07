package pt.haslab.mulletbench.database.InfluxDB;

import java.time.Instant;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;

@Measurement(name = "accel")
final class AccelMeasurement{

    /* @Column(tag = true)
    Integer subjectID;

    @Column(tag = true)
    Character activity; */

    @Column(timestamp = true)
    Instant time;

    @Column
    Float x;

    @Column
    Float y;

    @Column
    Float z;

    @Column(tag = true)
    String deviceId; //to separate data from workers into different devices

    public AccelMeasurement(String line, String deviceId){
        String[] values = line.split(",");
        if(values.length != 6){
            throw new IllegalArgumentException("Invalid line");
        }

        /* this.subjectID = Integer.parseInt(values[0]);
        this.activity = values[1].charAt(0); */
        long timestamp = Long.parseLong(values[2]);
        long timestampSeconds = Math.floorDiv(timestamp, 1_000_000_000L);
        long timestampNano = timestamp % 1_000_000_000L;

        this.time = Instant.ofEpochSecond(timestampSeconds, timestampNano);

        this.x = Float.parseFloat(values[3]);
        this.y = Float.parseFloat(values[4]);
        this.z = Float.parseFloat(values[5].substring(0, values[5].length() - 1));
        this.deviceId = deviceId;
    }
}
