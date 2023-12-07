package pt.haslab.mulletbench.queries.queryGenerators;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.haslab.mulletbench.ResourceAccess;
import pt.haslab.mulletbench.queries.queryBuilders.QueryBuilder;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;
import pt.haslab.mulletbench.utils.ClientOptions;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class GPSMPUQueryGenerator extends FloatsQueryGenerator {

    private static final Logger logger = LogManager.getLogger();

    public GPSMPUQueryGenerator(QueryBuilder builder, ClientOptions options, TimeController tc) {
        super(builder, options, 30, tc);
        this.columns = List.of("acc_x_dashboard", "acc_y_dashboard", "acc_z_dashboard", "acc_x_above_suspension",
            "acc_y_above_suspension", "acc_z_above_suspension", "acc_x_below_suspension", "acc_y_below_suspension",
            "acc_z_below_suspension", "gyro_x_dashboard", "gyro_y_dashboard", "gyro_z_dashboard",
            "gyro_x_above_suspension", "gyro_y_above_suspension", "gyro_z_above_suspension", "gyro_x_below_suspension",
            "gyro_y_below_suspension", "gyro_z_below_suspension", "mag_x_dashboard", "mag_y_dashboard",
            "mag_z_dashboard", "mag_x_above_suspension", "mag_y_above_suspension", "mag_z_above_suspension",
            "temp_dashboard", "temp_above_suspension", "temp_below_suspension", "latitude", "longitude", "speed");
    }

    @Override
    public void process(String dataFile) throws IOException {
        Iterator<String> fileIterator = ResourceAccess.getFileBufferedReader("/data/" + dataFile, this.getClass()).lines().iterator();

        int count = 0;
        while (fileIterator.hasNext()) {
            String line = fileIterator.next();
            String[] values = line.split(",");

            String[] splitTimestamp = values[0].split("\\.");
            long timestampSeconds = Long.parseLong(splitTimestamp[0]);
            long timestampDecimals = Long.parseLong(splitTimestamp[1]);

            long timestampNano = (long) (timestampDecimals * (100 / Math.pow(10, splitTimestamp[1].length() - 1)));

            processTimestamp((timestampSeconds * 1_000) + timestampNano);

            for (int i = 0; i < columns.size(); i++) {
                float value;
                if(i < 27){
                    value = Float.parseFloat(values[i+1]);
                } else {
                    value = Float.parseFloat(values[i+2]); //skip value 28
                }
                // for each column, register min, max and add to sum
                processValue(value, i);
            }

            count++;
        }

        logger.debug("Dataset processed: " + dataFile);
        processFinish(count);
    }
}
