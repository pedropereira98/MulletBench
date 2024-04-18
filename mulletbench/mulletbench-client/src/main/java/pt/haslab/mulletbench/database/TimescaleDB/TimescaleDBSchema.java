package pt.haslab.mulletbench.database.TimescaleDB;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public interface TimescaleDBSchema {

    public String getName();

    public List<String> getSchemas();
    public boolean addSensors(Connection connection);

    public void parse(PreparedStatement insertStatement, String record, String deviceId) throws SQLException;
    static TimescaleDBSchema getInstance(String dataset) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> clazz = Class.forName("pt.haslab.mulletbench.database.TimescaleDB." + dataset + "Schema");
        if (TimescaleDBSchema.class.isAssignableFrom(clazz)) {
            return (TimescaleDBSchema) clazz.getConstructor().newInstance();
        } else {
            throw new ClassNotFoundException("Class does not extend TimescaleDBSchema");
        }
    }

}
