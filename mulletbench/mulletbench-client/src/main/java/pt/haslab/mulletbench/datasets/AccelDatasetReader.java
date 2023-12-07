package pt.haslab.mulletbench.datasets;

import java.io.FileNotFoundException;

public class AccelDatasetReader extends DatasetReader {
    public AccelDatasetReader(String fileName, boolean currentTime, Dataset sharedDataset, String clientId) throws FileNotFoundException {
        super(fileName, currentTime, sharedDataset, clientId);
    }

    public AccelDatasetReader(String fileName, boolean currentTime) throws FileNotFoundException {
        super(fileName, currentTime);
    }

    @Override
    public long getTimestamp(String line) {
        return Long.parseLong(line.split(",")[2]);
    }

    public String changeTimestamp(String line, long timestamp){
        String[] splitLine = line.split(",");
        splitLine[2] = String.valueOf(timestamp);
        return String.join(",",splitLine);
    }
}
