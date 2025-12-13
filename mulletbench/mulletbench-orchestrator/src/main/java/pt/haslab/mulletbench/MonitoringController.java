package pt.haslab.mulletbench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.utils.MonitorMetricsProcessor;

public class MonitoringController {

    private static final Logger logger = LogManager.getLogger();

    String monitoringInterval;
    String resultsFolder;
    String clientContainerBase;
    private final List<Process> v1ClientMonitorProcesses = new ArrayList<>();
    private final List<Process> v1DatabaseMonitorProcesses = new ArrayList<>();
    private final List<Process[]> v2ClientMonitorProcesses = new ArrayList<>();
    private final List<Process[]> v2DatabaseMonitorProcesses = new ArrayList<>();

    public MonitoringController(String monitoringInterval, String resultsFolder, String clientContainerBase) {
        this.clientContainerBase = clientContainerBase;
        this.monitoringInterval = monitoringInterval;
        this.resultsFolder = resultsFolder;
    }

    private Process monitor(String containerName, String outputFileName, String address) {
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
            resultsFolder + "/" + "monitor-" + outputFileName,
            "-h",
            address,};
        try {
            Process p = Runtime.getRuntime().exec(nodeCommand);
            logger.debug(p.info().toString());
            logger.info("Started monitoring " + containerName);
            return p;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Process[] monitor(String containerName, String containerID, String outputFilename, String address) {
        String path = resultsFolder + "/" + containerName + "." + containerID;
        try {
            // Ensure results directory exists
            Files.createDirectories(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String globalOutput = path + "/global-" + outputFilename;
        String containerOutput = path + "/container-" + outputFilename;

        // Global metrics
        String[] globalCmd = new String[]{
            "pmrep", "-t", monitoringInterval, "-b", "MBytes",
            "-I", "cgroup.memory.current", "cgroup.memory.stat.inactive_file",
            "cgroup.cpu.stat.user", "cgroup.cpu.stat.system",
            "-o", "csv", "-f", "\"%Y-%m-%d %H:%M:%S.%f\"",
            "-F", globalOutput, "-h", address
        };

        // Container metrics
        String[] containerCmd = new String[]{
            "pmrep", "-t", monitoringInterval, "-b", "MBytes",
            "-I", "--container", containerName,
            "network.interface.in.bytes", "network.interface.out.bytes",
            "disk.dev.read_bytes", "disk.dev.write_bytes",
            "-o", "csv", "-f", "\"%Y-%m-%d %H:%M:%S.%f\"",
            "-F", containerOutput, "-h", address
        };

        try {
            Process globalProcess = Runtime.getRuntime().exec(globalCmd);
            Process containerProcess = Runtime.getRuntime().exec(containerCmd);

            System.out.println(globalProcess.info().toString());
            System.out.println(containerProcess.info().toString());
            System.out.println("Started monitoring: " + containerName + " (" + containerID + ")");
            return new Process[]{globalProcess, containerProcess};
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void monitorDatabase(String containerName, String containerID, String cgroupsVersion, String outputFileName, String address) {
        if (cgroupsVersion.equals("v2")) {
            v2DatabaseMonitorProcesses.add(monitor(containerName, containerID, outputFileName, address));
        } else {
            v1DatabaseMonitorProcesses.add(monitor(containerName, outputFileName, address));
        }
    }

    public void monitorClient(String clientName, String containerID, String cgroupsVersion, String outputFileName, String address) {
        if (cgroupsVersion.equals("v2")) {
            v2ClientMonitorProcesses.add(monitor(clientName, containerID, outputFileName, address));
        } else {
            v1ClientMonitorProcesses.add(monitor(clientName, outputFileName, address));
        }
    }

    public void stopMonitoringClients() {
        for (Process p : v1ClientMonitorProcesses) {
            p.destroy();
            logger.debug("Destroyed");
        }
        v1ClientMonitorProcesses.clear();
        for (Process[] pArr : v2ClientMonitorProcesses) {
            for (Process p : pArr) {
                p.destroy();
                logger.debug("Destroyed");
            }
        }
        // TODO - filter global processes and join with container specific ones
        v2ClientMonitorProcesses.clear();
    }

    public void stopMonitoringNodes() {
        for (Process p : v1DatabaseMonitorProcesses) {
            p.destroy();
            logger.debug("Destroyed");
        }
        v1DatabaseMonitorProcesses.clear();
        for (Process[] pArr : v2DatabaseMonitorProcesses) {
            for (Process p : pArr) {
                p.destroy();
                logger.debug("Destroyed");
            }
        }
        v2MonitorDataProcessing();
        v2DatabaseMonitorProcesses.clear();
    }

    private void v2MonitorDataProcessing() {
        // Data processing for cgroups v2 monitoring
        try {
            Files.list(Paths.get(resultsFolder))
                    .filter(Files::isDirectory)
                    .forEach(dirPath -> {
                        MonitorMetricsProcessor processor = new MonitorMetricsProcessor(dirPath, resultsFolder);
                        processor.processMetrics();
                    });
        } catch (IOException e) {
            logger.error("Error listing results folder: " + resultsFolder, e);
        }
    }
}
