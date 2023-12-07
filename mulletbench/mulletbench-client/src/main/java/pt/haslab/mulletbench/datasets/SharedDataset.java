package pt.haslab.mulletbench.datasets;

import pt.haslab.mulletbench.ResourceAccess;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SharedDataset implements Dataset {

    Map<String, Integer> workerIndex = new HashMap<>(); //keeps current iteration index for each worker

    ArrayList<String> dataset = new ArrayList<>();

    public SharedDataset(String fileName) throws FileNotFoundException {
        Iterator<String> fileIterator = ResourceAccess.getFileBufferedReader("/data/" + fileName, this.getClass()).lines().iterator();
        while(fileIterator.hasNext()){
            dataset.add(fileIterator.next());
        }
    }

    @Override
    public boolean hasNext(String id) {
        return dataset.size() > workerIndex.get(id);
    }

    @Override
    public String next(String id) {
        int i = workerIndex.get(id);
        workerIndex.put(id, i+1);
        return dataset.get(i);
    }

    @Override
    public void reload(String id) {
        workerIndex.put(id, 0);
    }

    @Override
    public Long lastTimestamp(DatasetReader datasetReader) {
        return datasetReader.getTimestamp(dataset.get(dataset.size()-1));
    }

    @Override
    public void start(String workerId){
        this.workerIndex.put(workerId, 0);
    }

}
