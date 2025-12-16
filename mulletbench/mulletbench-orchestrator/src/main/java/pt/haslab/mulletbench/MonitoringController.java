package pt.haslab.mulletbench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        String[] nodeCommand = new String[] {
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
                address, };
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
        String ioOutput = path + "/io-" + outputFilename;

        // Container network metrics
        String[] containerCmd = new String[] {
                "pmrep", "-t", monitoringInterval, "-b", "MBytes",
                "-I", "--container", containerName,
                "network.interface.in.bytes", "network.interface.out.bytes",
                "-o", "csv", "-f", "\"%Y-%m-%d %H:%M:%S.%f\"",
                "-F", containerOutput, "-h", address
        };

        String[] globalCmd = new String[] {
                "pmrep", "-t", monitoringInterval, "-b", "MBytes",
                "-I", "cgroup.memory.current", "cgroup.memory.stat.inactive_file",
                "cgroup.cpu.stat.user", "cgroup.cpu.stat.system",
                "-o", "csv", "-f", "\"%Y-%m-%d %H:%M:%S.%f\"",
                "-F", globalOutput, "-h", address
        };

        String[] ioCmd = new String[] {
                "pmrep", "-t", monitoringInterval, "-b", "MBytes",
                "-I", "cgroup.io.stat.rbytes", "cgroup.io.stat.wbytes",
                "-o", "csv", "-f", "\"%Y-%m-%d %H:%M:%S.%f\"",
                "-F", ioOutput, "-h", address
        };

        try {
            Process globalProcess = Runtime.getRuntime().exec(globalCmd);
            Process containerProcess = Runtime.getRuntime().exec(containerCmd);
            Process ioProcess = Runtime.getRuntime().exec(ioCmd);

            logProcessErrors(globalProcess, "GLOBAL-" + containerID);
            logProcessErrors(containerProcess, "CONTAINER-" + containerID);
            logProcessErrors(ioProcess, "IO-" + containerID);

            logger.info(globalProcess.info().toString());
            logger.info(containerProcess.info().toString());
            logger.info(ioProcess.info().toString());
            logger.info("Started monitoring: " + containerName + " (" + containerID + ")");
            return new Process[] { globalProcess, containerProcess, ioProcess };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void logProcessErrors(Process process, String identifier) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.error("[" + identifier + "] " + line);
                }
            } catch (IOException e) {
                logger.error("Error reading stderr for " + identifier, e);
            }
        }).start();
    }

    private void warmupPcpMetrics(String address) {
        logger.info("Warming up PCP metrics discovery for " + address);
        try {
            // Run multiple warmup queries to trigger full metric discovery
            // This forces PCP to enumerate all cgroups before we start actual monitoring
            String[][] warmupCommands = new String[][] {
                    new String[] { "pmrep", "-t", "1sec", "-s", "3", "-h", address,
                            "cgroup.memory.current", "cgroup.cpu.stat.system",
                            "cgroup.cpu.stat.user" },
                    new String[] { "pmrep", "-t", "1sec", "-s", "3", "-h", address,
                            "cgroup.io.stat.rbytes", "cgroup.io.stat.wbytes" },
                    new String[] { "pmrep", "-t", "1sec", "-s", "2", "-h", address,
                            "cgroup.memory.current", "cgroup.cpu.stat.system",
                            "cgroup.cpu.stat.user", "cgroup.io.stat.rbytes",
                            "cgroup.io.stat.wbytes" }
            };

            for (int i = 0; i < 5; i++) {
                logger.info("PCP warmup iteration " + (i + 1) + "/5 for " + address);
                for (String[] cmd : warmupCommands) {
                    Process p = Runtime.getRuntime().exec(cmd);
                    boolean finished = p.waitFor(10, TimeUnit.SECONDS);
                    if (!finished) {
                        logger.warn("Warmup command timed out, destroying process");
                        p.destroy();
                    } else {
                        logger.debug("Warmup command completed with exit code: " + p.exitValue());
                    }
                }
            }
            logger.info("PCP warmup completed for " + address);
        } catch (IOException | InterruptedException e) {
            logger.warn("Failed to warmup PCP metrics for " + address, e);
        }
    }

    public void monitorDatabase(String containerName, String containerID, String cgroupsVersion, String outputFileName,
            String address) {
        if (cgroupsVersion.equals("v2")) {
            // CRITICAL: Warmup PCP before starting monitoring
            warmupPcpMetrics(address);
            v2DatabaseMonitorProcesses.add(monitor(containerName, containerID, outputFileName, address));
        } else {
            v1DatabaseMonitorProcesses.add(monitor(containerName, outputFileName, address));
        }
    }

    public void monitorClient(String clientName, String containerID, String cgroupsVersion, String outputFileName,
            String address) {
        if (cgroupsVersion.equals("v2")) {
            // CRITICAL: Warmup PCP before starting monitoring
            warmupPcpMetrics(address);
            v2ClientMonitorProcesses
                    .add(monitor(clientContainerBase + clientName, containerID, outputFileName, address));
        } else {
            v1ClientMonitorProcesses.add(monitor(clientContainerBase + clientName, outputFileName, address));
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