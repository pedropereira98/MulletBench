package pt.haslab.mulletbench.datasets;

import java.io.FileNotFoundException;

public class GPSMPUDatasetReader extends DatasetReader {
    public GPSMPUDatasetReader(String fileName, boolean currentTime, Dataset sharedDataset, String clientId) throws FileNotFoundException {
        super(fileName, currentTime, sharedDataset, clientId);
    }

    public GPSMPUDatasetReader(String fileName, boolean currentTime) throws FileNotFoundException {
        super(fileName, currentTime);
    }

    @Override
    public long getTimestamp(String line) {
        String[] splitTimestamp = line.split(",")[0].split("\\.");
        long timestampSeconds = Long.parseLong(splitTimestamp[0]);
        long timestampDecimals = Long.parseLong(splitTimestamp[1]);

        long timestampNano = (long) (timestampDecimals * (100_000_000 / Math.pow(10, splitTimestamp[1].length()-1)));

        return (timestampSeconds * 1_000_000_000) + timestampNano;
    }

    @Override
    public String changeTimestamp(String line, long timestamp) {
        long nanos = timestamp % 1_000_000_000;
        int leftZeros = 0;
        while(nanos * (Math.pow(10, leftZeros)) < 100_000_000){
            leftZeros++;
        }
        long seconds = Math.floorDiv(timestamp, 1_000_000_000);
        String[] splitLine = line.split(",");
        splitLine[0] = seconds + "." + "0".repeat(leftZeros) + nanos;

        return String.join(",", splitLine);
    }
}
