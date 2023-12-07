package pt.haslab.mulletbench.database.IoTDB;

import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.write.record.Tablet;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;

import java.util.ArrayList;
import java.util.List;

public class GPSMPUSchema implements IoTDBSchema {

    private List<MeasurementSchema> schemas;

    public GPSMPUSchema(){
        this.schemas = new ArrayList<>();
        schemas.add(new MeasurementSchema("acc_x_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("acc_y_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("acc_z_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("acc_x_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("acc_y_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("acc_z_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("acc_x_below_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("acc_y_below_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("acc_z_below_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("gyro_x_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("gyro_y_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("gyro_z_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("gyro_x_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("gyro_y_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("gyro_z_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("gyro_x_below_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("gyro_y_below_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("gyro_z_below_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("mag_x_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("mag_y_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("mag_z_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("mag_x_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("mag_y_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("mag_z_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("temp_dashboard", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("temp_above_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("temp_below_suspension", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("latitude", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("longitude", TSDataType.DOUBLE));
        schemas.add(new MeasurementSchema("speed", TSDataType.DOUBLE));


    }

    @Override
    public List<MeasurementSchema> getSchemas() {
        return this.schemas;
    }

    @Override
    public Tablet parse(Tablet t, int rowIndex, String r) {
        String[] values = r.split(",");

        String[] splitTimestamp = values[0].split("\\.");
        long timestampSeconds = Long.parseLong(splitTimestamp[0]);
        long timestampDecimals = Long.parseLong(splitTimestamp[1]);

        long timestampNano = (long) (timestampDecimals * (100_000_000 / Math.pow(10, splitTimestamp[1].length()-1)));

        t.addTimestamp(rowIndex, (timestampSeconds * 1_000_000_000) + timestampNano);

        for (int s = 1; s < 32; s++) {
            if(s < 28){
                t.addValue(schemas.get(s-1).getMeasurementId(), rowIndex, Double.parseDouble(values[s]));
            } else if (s > 28) {
                t.addValue(schemas.get(s-2).getMeasurementId(), rowIndex, Double.parseDouble(values[s]));
            }
        }

        return t;
    }
}
