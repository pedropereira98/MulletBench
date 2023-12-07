package pt.haslab.mulletbench.database.InfluxDB;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;

import java.time.Instant;

@Measurement(name = "GPS_MPU")
final class GPSMPUMeasurement {
    @Column(timestamp = true)
    Instant timestamp;

    @Column
    Double acc_x_dashboard;

    @Column
    Double acc_y_dashboard;

    @Column
    Double acc_z_dashboard;

    @Column
    Double acc_x_above_suspension;

    @Column
    Double acc_y_above_suspension;

    @Column
    Double acc_z_above_suspension;

    @Column
    Double acc_x_below_suspension;

    @Column
    Double acc_y_below_suspension;

    @Column
    Double acc_z_below_suspension;

    @Column
    Double gyro_x_dashboard;

    @Column
    Double gyro_y_dashboard;

    @Column
    Double gyro_z_dashboard;

    @Column
    Double gyro_x_above_suspension;

    @Column
    Double gyro_y_above_suspension;

    @Column
    Double gyro_z_above_suspension;

    @Column
    Double gyro_x_below_suspension;

    @Column
    Double gyro_y_below_suspension;

    @Column
    Double gyro_z_below_suspension;

    @Column
    Double mag_x_dashboard;

    @Column
    Double mag_y_dashboard;

    @Column
    Double mag_z_dashboard;

    @Column
    Double mag_x_above_suspension;

    @Column
    Double mag_y_above_suspension;

    @Column
    Double mag_z_above_suspension;

    @Column
    Double temp_dashboard;

    @Column
    Double temp_above_suspension;

    @Column
    Double temp_below_suspension;

    @Column
    Double latitude;

    @Column
    Double longitude;

    @Column
    Double speed;

    @Column(tag = true)
    String deviceId;

    public GPSMPUMeasurement(String line, String deviceId){
        String[] values = line.split(",");

        if(values.length != 32){
            throw new IllegalArgumentException("Invalid line");
        }

        String[] splitTimestamp = values[0].split("\\.");
        long timestampSeconds = Long.parseLong(splitTimestamp[0]);
        long timestampDecimals = Long.parseLong(splitTimestamp[1]);

        long timestampNano = (long) (timestampDecimals * (100_000_000 / Math.pow(10, splitTimestamp[1].length()-1)));

        this.timestamp = Instant.ofEpochSecond(timestampSeconds, timestampNano);

        this.acc_x_dashboard = Double.parseDouble(values[1]);
        this.acc_y_dashboard = Double.parseDouble(values[2]);
        this.acc_z_dashboard = Double.parseDouble(values[3]);
        this.acc_x_above_suspension = Double.parseDouble(values[4]);
        this.acc_y_above_suspension = Double.parseDouble(values[5]);
        this.acc_z_above_suspension = Double.parseDouble(values[6]);
        this.acc_x_below_suspension = Double.parseDouble(values[7]);
        this.acc_y_below_suspension = Double.parseDouble(values[8]);
        this.acc_z_below_suspension = Double.parseDouble(values[9]);
        this.gyro_x_dashboard = Double.parseDouble(values[10]);
        this.gyro_y_dashboard = Double.parseDouble(values[11]);
        this.gyro_z_dashboard = Double.parseDouble(values[12]);
        this.gyro_x_above_suspension = Double.parseDouble(values[13]);
        this.gyro_y_above_suspension = Double.parseDouble(values[14]);
        this.gyro_z_above_suspension = Double.parseDouble(values[15]);
        this.gyro_x_below_suspension = Double.parseDouble(values[16]);
        this.gyro_y_below_suspension = Double.parseDouble(values[17]);
        this.gyro_z_below_suspension = Double.parseDouble(values[18]);
        this.mag_x_dashboard = Double.parseDouble(values[19]);
        this.mag_y_dashboard = Double.parseDouble(values[20]);
        this.mag_z_dashboard = Double.parseDouble(values[21]);
        this.mag_x_above_suspension = Double.parseDouble(values[22]);
        this.mag_y_above_suspension = Double.parseDouble(values[23]);
        this.mag_z_above_suspension = Double.parseDouble(values[24]);
        this.temp_dashboard = Double.parseDouble(values[25]);
        this.temp_above_suspension = Double.parseDouble(values[26]);
        this.temp_below_suspension = Double.parseDouble(values[27]);
        this.latitude = Double.parseDouble(values[29]);
        this.longitude = Double.parseDouble(values[30]);
        this.speed = Double.parseDouble(values[31]);

        this.deviceId = deviceId;
    }

    public static void main(String[] args) {
        String[] lines = new String[] {
            "1577218796.56,0.3651157531738281,0.16789339141845702,9.793960731201171,0.32762605888552765,0.17273289050590637,9.781861253543015,0.024797088623046453,0.1726105387369788,9.793823919677733,-0.1338958740234375,-0.018882751464843653,0.138092041015625,0.13844559832317088,0.1596590367759146,-0.0725722894435974,-0.04178001767113095,0.1673017229352678,-0.07811046781994045,22.878922325102874,-6.0405092592592595,1.7678739316239305,75.3409672289302,-7.537555962555963,-0.3347995783893226,35.065354778806125,33.340132386857164,31.782639949681013,1577218795.999,-27.717840956991854,-51.09886533963497,0.009127741",
            "1577218796.57,0.39264907226562495,0.1762730972290039,9.771215815429686,0.3814955962390433,0.18949230212700013,9.699261296267625,0.024797088623046453,0.19415835367838505,9.842905053710936,-0.0270843505859375,-0.0036239624023436,0.000762939453125,0.1689631764481709,0.0681063024009146,0.0952743902439026,0.019255138578869,0.3046308244977679,0.1507713681175595,22.87892232510288,-5.854647435897436,2.089305555555552,75.30024306968751,-6.4692409442409415,-5.222873422873423,34.993470512474914,33.17240243208434,31.782639949681013,1577218795.999,-27.717840956991854,-51.09886533963497,0.009127741",
            "1577218796.58,0.40940848388671874,0.18106150054931638,9.732908588867186,0.28333332817263707,0.18230969714653136,9.807000370974656,0.0032492736816402044,0.22767717692057254,9.888394885253906,0.1255035400390625,-0.1867294311523436,-0.090789794921875,-0.136212604801829,0.1596590367759146,0.1563095464939026,-0.3774733770461309,-0.1226152692522321,0.0287010556175595,23.434685620449507,-4.181891025641026,0.6428632478632437,75.46313970665823,-7.893660968660967,-4.687194097450509,34.993470512474914,33.41201665318837,31.926408482343422,1577218795.999,-27.717840956991854,-51.09886533963497,0.009127741",
            "1577218796.59,0.3711012573242187,0.16430208892822265,9.74966800048828,0.3144579497546683,0.23019373034965637,9.739962724490281,0.005643475341796455,0.1726105387369788,9.87163547363281,-0.0881195068359375,-0.0341415405273436,0.046539306640625,-0.075177448551829,0.0375887242759146,0.0647568121189026,0.049772716703869,-0.1836504255022321,0.0592186337425595,23.990448915796136,-5.482923789173789,1.12501068376068,75.46313970665823,-7.893660968660967,-4.687194097450509,34.96950909036451,33.22032527630515,31.926408482343422,1577218795.999,-27.717840956991854,-51.09886533963497,0.009127741"
        };

        long before = System.nanoTime();
        for(int i = 0; i < 1_000_000; i++){
            for(String line: lines){
                GPSMPUMeasurement measurement = new GPSMPUMeasurement(line, "asjfoas");
                Instant timestamp = measurement.timestamp;
                Double value = measurement.acc_x_above_suspension;
            }
        }
        long after = System.nanoTime();
        System.out.println("Took " + (after - before)/1_000_000);
        System.out.println(1_000_000 / ((after-before)/1_000_000_000) + " per second");
    }
}
