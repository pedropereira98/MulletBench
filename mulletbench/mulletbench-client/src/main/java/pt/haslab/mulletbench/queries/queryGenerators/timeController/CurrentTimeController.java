package pt.haslab.mulletbench.queries.queryGenerators.timeController;

import org.apache.logging.log4j.Logger;

import java.time.Instant;

public class CurrentTimeController extends TimeController {

    @Override
    public long getRangeSize() {
        return System.currentTimeMillis() - startOfRange.toEpochMilli();
    }

    @Override
    public Instant getStartOfRange() {
        return startOfRange;
    }

    @Override
    public Instant getEndOfRange() {
        return Instant.now();
    }

    @Override
    public void processTimestamp(Long timestamp) {
    }

    public void processFinish(Logger logger){
    }
}
