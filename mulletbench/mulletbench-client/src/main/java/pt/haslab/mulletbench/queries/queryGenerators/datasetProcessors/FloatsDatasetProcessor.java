package pt.haslab.mulletbench.queries.queryGenerators.datasetProcessors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.queries.queryGenerators.timeController.TimeController;

public abstract class FloatsDatasetProcessor extends DatasetProcessor {

    private static final Logger logger = LogManager.getLogger();

    private final double[] sums;
    private final double[] squaredSums;

    private final double[] averages;
    private final double[] standardDeviations;
    // median would also be interesting.
    // how to calculate a median in a stream?

    private final float[] mins;
    private final float[] maxs;

    public FloatsDatasetProcessor(TimeController tc, int numFields) {
        super(tc);

        sums = new double[numFields];
        squaredSums = new double[numFields];
        averages = new double[numFields];
        standardDeviations = new double[numFields];
        mins = new float[numFields];
        maxs = new float[numFields];

        for (int i = 0; i < numFields; i++) {
            sums[i] = 0;
            squaredSums[i] = 0;
            mins[i] = Float.MAX_VALUE;
            maxs[i] = Float.MAX_VALUE * -1;
        }
    }

    public double[] getSums() {
        return sums;
    }

    public double[] getSquaredSums() {
        return squaredSums;
    }

    public double[] getAverages() {
        return averages;
    }

    public double[] getStandardDeviations() {
        return standardDeviations;
    }

    public float[] getMins() {
        return mins;
    }

    public float[] getMaxs() {
        return maxs;
    }

    protected void processValue(Float value, int column) {
        sums[column] += value;
        squaredSums[column] += value * value;
        maxs[column] = Math.max(maxs[column], value);
        mins[column] = Math.min(mins[column], value);
    }

    protected void processFinish(int count) {
        // use sum, squaredSums and count to calculate average and std dev. for each column
        for (int i = 0; i < columns.size(); i++) {
            averages[i] = sums[i] / count;
            standardDeviations[i] = Math.sqrt(squaredSums[i] / count - averages[i] * averages[i]);
        }

        logger.debug("Processed " + count + " lines");

        for (String column : columns) {
            logger.debug("Column " + column + " average: " + averages[columns.indexOf(column)]);
            logger.debug("Column " + column + " standard deviation: " + standardDeviations[columns.indexOf(column)]);
            logger.debug("Column " + column + " min: " + mins[columns.indexOf(column)]);
            logger.debug("Column " + column + " max: " + maxs[columns.indexOf(column)]);
        }

        timeController.processFinish(logger);

    }

}
