package pt.haslab.mulletbench.workers;

import pt.haslab.mulletbench.database.DatabaseConnector;
import pt.haslab.mulletbench.stats.Stats;

public abstract class Worker implements Runnable{
    static int numWorkers;

    static boolean hold = false;
    DatabaseConnector connector;
    Stats stats;
    String workerId;
    int rateInterval;

    int workerNumber;

    public Worker(DatabaseConnector connector, Stats stats, String clientId, int workerNumber){
        this.stats = stats;
        this.connector = connector;
        this.workerId = clientId + "_d" + workerNumber;
        this.workerNumber = workerNumber;
    }

    //stagger workers by waiting up to interval length
    public void stagger(){
        try {
            long staggerInterval = (long) (rateInterval / numWorkers) * (workerNumber);
            if(staggerInterval > 0){
                Thread.sleep(staggerInterval);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setNumWorkers(int numWorkers) {
        Worker.numWorkers = numWorkers;
    }

    public abstract void run();

}
