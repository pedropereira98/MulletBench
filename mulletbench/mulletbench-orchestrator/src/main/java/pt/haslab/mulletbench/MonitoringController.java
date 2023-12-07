package pt.haslab.mulletbench;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MonitoringController {
    private static final Logger logger = LogManager.getLogger();

    String monitoringInterval;
    String resultsFolder;
    String clientContainerBase;
    private final List<Process> clientMonitorProcesses = new ArrayList<>();
    private final List<Process> databaseMonitorProcesses = new ArrayList<>();

    public MonitoringController(String monitoringInterval, String resultsFolder, String clientContainerBase){
        this.clientContainerBase = clientContainerBase;
        this.monitoringInterval = monitoringInterval;
        this.resultsFolder = resultsFolder;
    }

    private Process monitor(String containerName, String outputFileName, String address){
        String[] nodeCommand = new String[]{
            "pmrep",
            "-t",
            monitoringInterval,
            "-b",
            "MBytes",
            "-I",
            "--container",
            containerName,
            "network.interface.in.bytes",
            "network.interface.out.bytes",
            "cgroup.memory.usage",
            "cgroup.cpuacct.stat.system",
            "cgroup.cpuacct.stat.user",
            "cgroup.blkio.all.throttle.io_service_bytes.read",
            "cgroup.blkio.all.throttle.io_service_bytes.write",
            "-o",
            "csv",
            "-f",
            "\"%Y-%m-%d %H:%M:%S.%f\"",
            "-F",
            resultsFolder + "/" +  "monitor-" + outputFileName,
            "-h",
            address,
        };
        try {
            Process p = Runtime.getRuntime().exec(nodeCommand);
            logger.debug(p.info().toString());
            logger.info("Started monitoring " + containerName);
            return p;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void monitorDatabase(String containerName, String outputFileName, String address){
        databaseMonitorProcesses.add(monitor(containerName, outputFileName, address));
    }

    public void monitorClient(String clientName, String outputFileName, String address){
        clientMonitorProcesses.add(monitor(clientContainerBase + clientName, outputFileName, address));
    }

    public void stopMonitoringClients(){
        for(Process p: clientMonitorProcesses){
            p.destroy();
            logger.debug("Destroyed");
        }
        clientMonitorProcesses.clear();
    }

    public void stopMonitoringNodes(){
        for(Process p: databaseMonitorProcesses){
            p.destroy();
            logger.debug("Destroyed");
        }
        databaseMonitorProcesses.clear();
    }
}
