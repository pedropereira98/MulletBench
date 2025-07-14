package pt.haslab.mulletbench.queries.queryGenerators.datasetProcessors;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import pt.haslab.mulletbench.queries.queryBuilders.QueryBuilder;
import pt.haslab.mulletbench.queries.queryGenerators.QueryGenerator;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;
import pt.haslab.mulletbench.utils.ClientOptions;

public abstract class DatasetProcessor {

    protected TimeController timeController;
    protected List<String> columns;

    public DatasetProcessor(TimeController tc){
        this.timeController = tc;
    }

    public static DatasetProcessor getInstance(String dataset, TimeController tc) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> clazz = Class.forName("pt.haslab.mulletbench.queries.queryGenerators.datasetProcessors." + dataset + "DatasetProcessor");
        if (DatasetProcessor.class.isAssignableFrom(clazz)) {
            return (DatasetProcessor) clazz.getConstructor(TimeController.class).newInstance(tc);
        } else {
            throw new ClassNotFoundException("Class does not extend DatasetProcessor");
        }
    }
    
    protected void processTimestamp(Long timestamp){
        timeController.processTimestamp(timestamp);
    }

    public abstract void process(String dataFile) throws IOException;

    public List<String> getColumns() {
        return new ArrayList<>(columns);
    }
}
