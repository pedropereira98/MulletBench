package pt.haslab.mulletbench.queries.queryGenerators;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.queries.queryBuilders.QueryBuilder;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;
import pt.haslab.mulletbench.utils.ClientOptions;
import pt.haslab.mulletbench.ResourceAccess;

public class AccelQueryGenerator extends FloatsQueryGenerator {

    private static final Logger logger = LogManager.getLogger();

    public AccelQueryGenerator(QueryBuilder builder, ClientOptions options, TimeController tc) {
        super(builder, options, 3, tc);
        this.columns = List.of("x", "y", "z");
    }

    public void process(String dataFile) throws IOException{
        Iterator<String> fileIterator= ResourceAccess.getFileBufferedReader("/data/" + dataFile, this.getClass()).lines().iterator();

        int count = 0;
        while(fileIterator.hasNext()){
            String line = fileIterator.next();
            String[] values = line.split(",");

            long lineTimestamp = Long.parseLong(values[2])/1000000;
            processTimestamp(lineTimestamp);

            for(int i = 0; i < columns.size(); i++){
                float value;
                if(i == 2){
                    value = Float.parseFloat(values[5].substring(0, values[5].length() - 1));
                } else {
                    value = Float.parseFloat(values[i+3]);
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

