package pt.haslab.mulletbench.datasets;

import java.io.FileNotFoundException;

public interface Dataset {
    public boolean hasNext(String id);

    public String next(String id);

    public void reload(String id) throws FileNotFoundException;

    public Long lastTimestamp(DatasetReader datasetReader);

    public void start(String clientId);
}
