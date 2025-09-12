package pt.haslab.mulletbench.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import pt.haslab.mulletbench.TimeProvider;

public class QueryDumper {
    private final PrintWriter queryWriter;
    private final String queryFile;

    public QueryDumper() throws FileNotFoundException {
        // create queries file
        queryFile = "/home/app/output/queries" + TimeProvider.getNanoTime() + ".txt";
        File file = new File(queryFile);
        file.getParentFile().mkdirs();
        queryWriter = new PrintWriter(file);
    }

    // synchronized to avoid interleaving of writes, since multiple workers may call this method concurrently
    public synchronized void dumpQuery(String workerId, String queryString) {
        queryWriter.println(workerId + " " + TimeProvider.getNanoTime() + " " + queryString);
    }

    public void close() {
        queryWriter.close();
    }
}