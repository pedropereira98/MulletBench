package pt.haslab.mulletbench.database.InfluxDB;

import java.time.Instant;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;

// From Gas sensor array under dynamic gas mixtures Data Set
// Dataset has 16 sensors but only 4 are used here for testing
@Measurement(name = "gas_mixture")
final class GasMixtureMeasurement{
    @Column(timestamp = true)
    Instant time;

    @Column
    Float co;

    @Column
    Float ethylene;

    @Column
    Float s1;

    @Column
    Float s2;

    @Column
    Float s3;

    @Column
    Float s4;

    @Column(tag = true)
    String deviceId; //to separate data from workers into different devices

    public GasMixtureMeasurement(String line, String deviceId){
        String[] values = line.split("\\s+"); //split my variable number of whitespaces
        if(values.length < 7){
            throw new IllegalArgumentException("Invalid line");
        }

        long timestamp = Long.parseLong(values[0]);
        long timestampSeconds = Math.floorDiv(timestamp, 1_000_000_000L);
        long timestampNano = timestamp % 1_000_000_000L;

        this.time = Instant.ofEpochSecond(timestampSeconds, timestampNano);
        this.co = Float.parseFloat(values[1]);
        this.ethylene = Float.parseFloat(values[2]);
        this.s1 = Float.parseFloat(values[3]);
        this.s2 = Float.parseFloat(values[4]);
        this.s3 = Float.parseFloat(values[5]);
        this.s4 = Float.parseFloat(values[6]);
        this.deviceId = deviceId;
    }

    public static void main(String[] args) {
        String line = "0.00    0.00    0.00    -50.85  -1.95   -41.82  1.30    -4.07   -28.73  -13.49  -3.25   55139.95 50669.50 9626.26 9762.62 24544.02 21420.68 7650.61 6928.42";
        GasMixtureMeasurement gmm = new GasMixtureMeasurement(line, "device0");

        System.out.println(gmm.s2);
    }
}
