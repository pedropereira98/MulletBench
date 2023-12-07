package pt.haslab.mulletbench.queries.queryGenerators.timeController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.apache.logging.log4j.Logger;

public class DatasetTimeController extends TimeController {

    Float numberOfLoops;

    public DatasetTimeController(float numberOfLoops){
        this.numberOfLoops = numberOfLoops;
    }

    @Override
    public long getRangeSize() {
        return rangeSize;
    }

    @Override
    public Instant getStartOfRange() {
        return startOfRange;
    }

    @Override
    public Instant getEndOfRange() {
        return endOfRange;
    }

    @Override
    public void processTimestamp(Long timestamp) {
        endTimestamp = Math.max(endTimestamp, timestamp);
        startTimestamp = Math.min(startTimestamp, timestamp);
    }

    public void processFinish(Logger logger){
        this.startOfRange = Instant.ofEpochMilli(startTimestamp);
        Long datasetRange = endTimestamp - startTimestamp;
        this.rangeSize = (long) (datasetRange * numberOfLoops);
        this.endOfRange = this.startOfRange.plus(rangeSize, ChronoUnit.MILLIS); //from range of dataset and number of loops performed when inserting dataset, calculate actual end of inserted data

        logger.debug("Start timestamp: " + startTimestamp);
        logger.debug("End timestamp: " + endTimestamp);
        logger.debug("Start date: " + startOfRange);
        logger.debug("Dataset end date: " + Instant.ofEpochMilli(endTimestamp));
        logger.debug("Estimated end date: " + endOfRange);
        logger.debug("Time range: " + rangeSize);
    }


}
