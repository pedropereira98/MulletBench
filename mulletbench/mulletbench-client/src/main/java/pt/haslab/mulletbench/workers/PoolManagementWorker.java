package pt.haslab.mulletbench.workers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ForkJoinPool;

public class PoolManagementWorker implements Runnable {
    private static final Logger logger = LogManager.getLogger();
    private int rateInterval;

    public PoolManagementWorker(float rate){
        this.rateInterval = (int) (1000/rate) * 2;
    }
    @Override
    public void run() {
        ForkJoinPool commonPool = ForkJoinPool.commonPool();
        long maxPoolQueueSize = 4L * Worker.numWorkers;
        boolean interrupted = false;
        while(!interrupted){
            int queueSize = commonPool.getQueuedSubmissionCount();
            Worker.hold = queueSize > maxPoolQueueSize;
            logger.debug("Hold is " + Worker.hold + " size: " + queueSize);
            try {
                Thread.sleep(rateInterval);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
    }
}
