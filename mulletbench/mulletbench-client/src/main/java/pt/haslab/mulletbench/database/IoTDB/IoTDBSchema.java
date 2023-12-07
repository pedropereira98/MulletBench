package pt.haslab.mulletbench.database.IoTDB;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.apache.iotdb.tsfile.write.record.Tablet;
import org.apache.iotdb.tsfile.write.schema.MeasurementSchema;

interface IoTDBSchema {
    List<MeasurementSchema> getSchemas();

    Tablet parse(Tablet t, int rowIndex, String r);

    static IoTDBSchema getInstance(String dataset) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> clazz = Class.forName("pt.haslab.mulletbench.database.IoTDB." + dataset + "Schema");
        if (IoTDBSchema.class.isAssignableFrom(clazz)) {
            return (IoTDBSchema) clazz.getConstructor().newInstance();
        } else {
            throw new ClassNotFoundException("Class does not extend IoTDBSchema");
        }
    }
}
