package pt.haslab.mulletbench.database.IoTDB;

import java.util.ArrayList;
import java.util.List;

import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.write.record.Tablet;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;

// From Gas sensor array under dynamic gas mixtures Data Set
// Dataset has 16 sensors but only 4 are used here for testing
class GasMixtureSchema implements IoTDBSchema {

    protected List<MeasurementSchema> schemas;

    public GasMixtureSchema(){
        this.schemas = new ArrayList<>();
        this.schemas.add(new MeasurementSchema("co", TSDataType.FLOAT, TSEncoding.PLAIN));
        this.schemas.add(new MeasurementSchema("ethylene", TSDataType.FLOAT, TSEncoding.PLAIN));
        this.schemas.add(new MeasurementSchema("s1", TSDataType.FLOAT, TSEncoding.PLAIN));
        this.schemas.add(new MeasurementSchema("s2", TSDataType.FLOAT, TSEncoding.PLAIN));
        this.schemas.add(new MeasurementSchema("s3", TSDataType.FLOAT, TSEncoding.PLAIN));
        this.schemas.add(new MeasurementSchema("s4", TSDataType.FLOAT, TSEncoding.PLAIN));
    }

    public List<MeasurementSchema> getSchemas(){
        return this.schemas;
    }

    public Tablet parse(Tablet t, int rowIndex, String r){
        String[] values = r.split("\\s+");
        long timestamp = Long.parseLong(values[0]);
        t.addTimestamp(rowIndex, timestamp);

        for (int s = 0; s < 6; s++) {
            float value = Float.parseFloat(values[s+1]);

            t.addValue(schemas.get(s).getMeasurementId(), rowIndex, value);
        }

        return t;
    }
}
