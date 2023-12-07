package pt.haslab.mulletbench.database.InfluxDB;

import java.lang.reflect.Constructor;

public class MeasurementFactory<M> {
    private final Constructor<M> measurementConstructor;

    public static MeasurementFactory getInstance(String datasetName) throws ClassNotFoundException, NoSuchMethodException {
        return new MeasurementFactory(Class.forName("pt.haslab.mulletbench.database.InfluxDB." + datasetName + "Measurement"));
    }

    public MeasurementFactory(Class<M> measurementClass) throws NoSuchMethodException {
        this.measurementConstructor = measurementClass.getConstructor(String.class, String.class);
    }

    public M getMeasurement(String line, String deviceId) {
        try{
            return measurementConstructor.newInstance(line, deviceId);
        } catch (Exception e){
            System.err.println("Error creating measurement" + e);
        }
        return null;
    }
}
