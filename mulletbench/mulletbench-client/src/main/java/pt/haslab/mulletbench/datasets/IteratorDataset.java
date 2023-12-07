package pt.haslab.mulletbench.datasets;

import pt.haslab.mulletbench.ResourceAccess;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

public class IteratorDataset implements Dataset {
    private Iterator<String> fileIterator;
    private final String fileName;

    public IteratorDataset(String fileName) throws FileNotFoundException {
        this.fileName = fileName;
        this.fileIterator = ResourceAccess.getFileBufferedReader("/data/" + fileName, this.getClass()).lines().iterator();
    }

    @Override
    public boolean hasNext(String id) {
        return fileIterator.hasNext();
    }

    @Override
    public String next(String id) {
        return fileIterator.next();
    }

    @Override
    public void reload(String id) throws FileNotFoundException {
        this.fileIterator = ResourceAccess.getFileBufferedReader("/data/" + this.fileName, this.getClass()).lines().iterator();
    }

    @Override
    public Long lastTimestamp(DatasetReader datasetReader) {
        AtomicLong lastTimestamp = new AtomicLong();
        fileIterator.forEachRemaining(entry -> lastTimestamp.set(datasetReader.getTimestamp(entry)));
        return lastTimestamp.get();
    }

    @Override
    public void start(String clientId) {

    }


}
