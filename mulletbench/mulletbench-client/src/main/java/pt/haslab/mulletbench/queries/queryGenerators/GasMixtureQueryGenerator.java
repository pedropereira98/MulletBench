package pt.haslab.mulletbench.queries.queryGenerators;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.queries.queryBuilders.QueryBuilder;
import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;
import pt.haslab.mulletbench.utils.ClientOptions;
import pt.haslab.mulletbench.ResourceAccess;

public class GasMixtureQueryGenerator extends FloatsQueryGenerator {

    private static final Logger logger = LogManager.getLogger();

    public GasMixtureQueryGenerator(QueryBuilder builder, ClientOptions options, TimeController tc) {
        super(builder, options, 6, tc);
        this.columns = List.of("co", "ethylene", "s1", "s2", "s3", "s4");
    }

    @Override
    public void process(String dataFile) throws IOException {
        Iterator<String> fileIterator= ResourceAccess.getFileBufferedReader("/data/" + dataFile, this.getClass()).lines().iterator();

        int count = 0;
        while(fileIterator.hasNext()){
            String line = fileIterator.next();

            String[] values = line.split("\\s+"); //split my variable number of whitespace

            // long lineTimestamp = Long.parseLong(values[2])/1000000;
            processTimestamp(Instant.now().toEpochMilli());

            for(int i = 0; i < columns.size(); i++){
                float value = Float.parseFloat(values[i+1]);

                // for each column, register min, max and add to sum
                processValue(value, i);
            }

            count++;
        }

        logger.debug("Dataset processed: " + dataFile);
        processFinish(count);

    }
}
