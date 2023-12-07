package pt.haslab.mulletbench.datasets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.haslab.mulletbench.TimeProvider;

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.*;

public abstract class DatasetReader {
    private static final Logger logger = LogManager.getLogger();

    private Dataset dataset;
    private String workerId;
    private final String fileName;

    private final boolean currentTime;
    private int looped = 0;

    private long datasetTimestampDiff = Long.MIN_VALUE;


    private static Class<? extends DatasetReader> getClass(String dataset) throws ClassNotFoundException {
        Class<?> clazz = Class.forName("pt.haslab.mulletbench.datasets." + dataset + "DatasetReader");
        if (DatasetReader.class.isAssignableFrom(clazz)) {
            return (Class<? extends DatasetReader>) clazz;
        } else {
            throw new ClassNotFoundException("Class does not extend Dataset");
        }
    }

    public static DatasetReader getInstance(String fileName, String datasetName, boolean currentTime) throws ClassNotFoundException {
        Class<? extends DatasetReader> clazz = DatasetReader.getClass(datasetName);

        Constructor<? extends DatasetReader> constructor;
        try {
            constructor = clazz.getConstructor(String.class, boolean.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(clazz.getSimpleName() + " does not implement a " +  clazz.getSimpleName() + "(String filename, boolean currentTime) constructor method");
        }

        try {
            return constructor.newInstance(fileName, currentTime);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static DatasetReader getInstance(String fileName, String datasetName, boolean currentTime, Dataset sharedDataset, String workerId) throws ClassNotFoundException {
        Class<? extends DatasetReader> clazz = DatasetReader.getClass(datasetName);

        Constructor<? extends DatasetReader> constructor;
        try {
            constructor = clazz.getConstructor(String.class, boolean.class, Dataset.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(clazz.getSimpleName() + " does not implement a " +  clazz.getSimpleName() + "(String filename, boolean currentTime, Dataset sharedDataset, String workerId)) constructor method");
        }

        try {
            return constructor.newInstance(fileName, currentTime, sharedDataset, workerId);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public DatasetReader(String fileName, boolean currentTime, Dataset sharedDataset, String workerId) throws FileNotFoundException {
        this.dataset = sharedDataset;
        this.workerId = workerId;
        dataset.start(workerId);
        if(!currentTime){
            long firstTimestamp = getTimestamp(dataset.next(this.workerId));
            long secondTimestamp = getTimestamp(dataset.next(this.workerId));

            long lastTimestamp = dataset.lastTimestamp(this);
            this.datasetTimestampDiff = lastTimestamp - firstTimestamp + (secondTimestamp - firstTimestamp);
            logger.debug("Dataset diff" + datasetTimestampDiff);
            logger.debug("From " + Instant.ofEpochMilli(firstTimestamp/1_000_000) + " to " + Instant.ofEpochMilli(lastTimestamp/1_000_000));

            this.dataset.reload(workerId);
        }

        this.fileName = fileName;
        this.currentTime = currentTime;
    }

    public DatasetReader(String fileName, boolean currentTime) throws FileNotFoundException {
        this.dataset = new IteratorDataset(fileName);
        this.workerId = "";
        if(!currentTime){
            long firstTimestamp = getTimestamp(dataset.next(this.workerId));
            long secondTimestamp = getTimestamp(dataset.next(this.workerId));

            long lastTimestamp = dataset.lastTimestamp(this);
            this.datasetTimestampDiff = lastTimestamp - firstTimestamp + (secondTimestamp - firstTimestamp);
            logger.debug("Dataset diff" + datasetTimestampDiff);
            logger.debug("From " + Instant.ofEpochMilli(firstTimestamp/1_000_000) + " to " + Instant.ofEpochMilli(lastTimestamp/1_000_000));

            this.dataset.reload(workerId);
        }

        this.fileName = fileName;
        this.currentTime = currentTime;
    }

    public abstract long getTimestamp(String line);

    public abstract String changeTimestamp(String line, long timestamp);

    public void reload() {
        try {
            this.dataset.reload(workerId);
            if(!currentTime){
                looped++;
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> injectCurrentTimestamps(List<String> measurements, int amount, int rateIntervalMilli){
        long rateIntervalNano = rateIntervalMilli * 1_000_000L;

        if(amount > rateIntervalNano){
            logger.error("Worker insertion rate too high for timestamp injection");
            return measurements;
        }

//        If current timestamp is 1000000 in ns, rate interval is 10000 and amount is 10, this will result in the sequence of timestamps
//        990000, 991000, 992000, 993000, 994000, 995000, 996000, 997000, 998000, 999000
//        And next sequence will be
//        1000000, 1001000, 1002000, 1003000, 1004000, 1005000, 1006000, 1007000, 1008000, 1009000
        long currentTimeNano = TimeProvider.getNanoTime(); // nanoTime is bad for wall-clock time
        long startTime = currentTimeNano - rateIntervalNano;
        long intervalBetweenMeasurement = rateIntervalNano / amount;

        for (final ListIterator<String> it = measurements.listIterator(); it.hasNext();) {
            final String element = it.next();
            it.set(changeTimestamp(element, startTime));
            startTime+=intervalBetweenMeasurement;
        }

        return measurements;
    }

    private List<String> injectPastTimestamps(List<String> measurements) {
        for (final ListIterator<String> it = measurements.listIterator(); it.hasNext();) {
            final String element = it.next();
            final long timestamp = getTimestamp(element);
            it.set(changeTimestamp(element, timestamp + (this.datasetTimestampDiff * looped)));
        }
        return measurements;
    }

    public List<String> read(int amount, int rateInterval){
        List<String> measurements = new LinkedList<>();

        while(measurements.size() < amount){
            if(!dataset.hasNext(workerId)){
                break;
            }
            // current timestamp insertion would go here
            measurements.add(dataset.next(workerId));
        }

        if(currentTime){
            measurements = injectCurrentTimestamps(measurements, amount, rateInterval);
        } else if(looped > 0){
            measurements = injectPastTimestamps(measurements);
        }

        return measurements;
    }



    public List<String> read(int amount){
        List<String> measurements = new ArrayList<>(amount);

        while(measurements.size() < amount){
            if(!dataset.hasNext(workerId)){
                break;
            }
            // current timestamp insertion would go here
            measurements.add(dataset.next(workerId));
        }

        return measurements;
    }

    public boolean hasNext(){
        return dataset.hasNext(workerId);
    }

    public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<? extends DatasetReader> clazz = DatasetReader.getClass("GPSMPU");

        System.out.println(clazz.getSimpleName());

        Constructor<? extends DatasetReader> constructor = clazz.getConstructor(String.class);

        DatasetReader datasetReader = constructor.newInstance("dataset_gps_mpu_left.csv");

        Long timestamp = datasetReader.getTimestamp("1577218796.59,0.3711012573242187,0.16430208892822265,9.74966800048828,0.3144579497546683,0.23019373034965637,9.739962724490281,0.005643475341796455,0.1726105387369788,9.87163547363281,-0.0881195068359375,-0.0341415405273436,0.046539306640625,-0.075177448551829,0.0375887242759146,0.0647568121189026,0.049772716703869,-0.1836504255022321,0.0592186337425595,23.990448915796136,-5.482923789173789,1.12501068376068,75.46313970665823,-7.893660968660967,-4.687194097450509,34.96950909036451,33.22032527630515,31.926408482343422,1577218795.999,-27.717840956991854,-51.09886533963497,0.009127741");

        System.out.println(timestamp);
    }
}
