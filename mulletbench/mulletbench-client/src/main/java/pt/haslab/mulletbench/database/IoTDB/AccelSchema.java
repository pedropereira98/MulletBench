package pt.haslab.mulletbench.database.IoTDB;

import java.util.ArrayList;
import java.util.List;

import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.write.record.Tablet;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;

class AccelSchema implements IoTDBSchema {
    private List<MeasurementSchema> schemas;

    public AccelSchema(){
        this.schemas = new ArrayList<>();
        this.schemas.add(new MeasurementSchema("x", TSDataType.FLOAT, TSEncoding.PLAIN));
        this.schemas.add(new MeasurementSchema("y", TSDataType.FLOAT, TSEncoding.PLAIN));
        this.schemas.add(new MeasurementSchema("z", TSDataType.FLOAT, TSEncoding.PLAIN));

        // System.out.println(this.schemas);
    }

    public List<MeasurementSchema> getSchemas(){
        return this.schemas;
    }

    public Tablet parse(Tablet tablet, int rowIndex, String r){
        String[] values = r.split(",");

        // Before adding the /1000000 so the date is in 4499
        // Using original timestamp has significant performance implications
//        System.out.println(values[2]);
//        System.out.println(Instant.ofEpochMilli(Long.parseLong(values[2])/1_000_000L));
        long timestamp = Long.parseLong(values[2]);

        tablet.addTimestamp(rowIndex, timestamp);

        // System.out.println("Adding values for " + timestamp);

        for (int s = 0; s < 3; s++) {
            float value;
            if(s<2){
                value = Float.parseFloat(values[s+3]);
            } else {
                value = Float.parseFloat(values[5].substring(0, values[5].length() - 1));
            }

            tablet.addValue(schemas.get(s).getMeasurementId(), rowIndex, value);
        }

        return tablet;
    }
}
