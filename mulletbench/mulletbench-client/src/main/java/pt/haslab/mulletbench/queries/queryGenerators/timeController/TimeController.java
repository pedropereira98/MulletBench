package pt.haslab.mulletbench.queries.queryGenerators.timeController;

import org.apache.logging.log4j.Logger;

import java.time.Instant;

public abstract class TimeController {
    protected Instant startOfRange;
    protected Instant endOfRange;
    protected long startTimestamp = Long.MAX_VALUE;
    protected long endTimestamp = Long.MIN_VALUE;
    protected long rangeSize;

    public void setStart(long timestamp){
        this.startTimestamp = timestamp;
        this.startOfRange = Instant.ofEpochMilli(startTimestamp);
    }

    public abstract long getRangeSize();

    public abstract Instant getStartOfRange();

    public abstract Instant getEndOfRange();

    public abstract void processTimestamp(Long timestamp);

    public abstract void processFinish(Logger logger);
}
