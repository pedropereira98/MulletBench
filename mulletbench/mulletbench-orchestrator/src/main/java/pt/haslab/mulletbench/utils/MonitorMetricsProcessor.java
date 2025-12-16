package pt.haslab.mulletbench.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
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

public class MonitorMetricsProcessor {
    private static final Logger logger = LogManager.getLogger();

    private final Path dirPath;
    private final String resultsFolder;

    public MonitorMetricsProcessor(Path dirPath, String resultsFolder) {
        this.dirPath = dirPath;
        this.resultsFolder = resultsFolder;
    }

    public void processMetrics() {
        String dirName = dirPath.getFileName().toString();
        try {
            // Assuming directory name format: <containerName>.<containerID>
            String[] parts = dirName.split("\\.", 2);
            if (parts.length != 2) {
                logger.warn("Unexpected directory name format: " + dirName);
                return;
            }
            String containerID = parts[1];

            // get all files in the directory
            List<String> files = Files.list(dirPath).map(Path::getFileName).map(Path::toString).toList();

            String globalMetricsFile = "";
            String containerMetricsFile = "";
            String ioMetricsFile = "";

            for (String file : files) {
                if (file.startsWith("global-")) {
                    globalMetricsFile = file;
                } else if (file.startsWith("container-")) {
                    containerMetricsFile = file;
                } else if (file.startsWith("io-")) {
                    ioMetricsFile = file;
                }
            }

            if (globalMetricsFile.isEmpty() || containerMetricsFile.isEmpty() || ioMetricsFile.isEmpty()) {
                logger.warn("Missing monitoring files in directory: " + dirName);
                return;
            }

            String globalMetricsFilePath = dirPath + "/" + globalMetricsFile;
            String containerMetricsFilePath = dirPath + "/" + containerMetricsFile;
            String ioMetricsFilePath = dirPath + "/" + ioMetricsFile;

            String[] auxParts = globalMetricsFile.split("-");

            if (auxParts.length < 2) {
                logger.warn("Unexpected container input file format: " + globalMetricsFile);
                return;
            }

            String outputFileName = "monitor-" + auxParts[1];
            String filteredGlobalMetricsFile = dirPath + "/global-filtered-" + outputFileName;
            String filteredIoMetricsFile = dirPath + "/io-filtered-" + outputFileName;

            filterGlobalMetrics(globalMetricsFilePath, filteredGlobalMetricsFile, containerID);
            filterIoMetrics(ioMetricsFilePath, filteredIoMetricsFile, containerID);
            joinMetrics(filteredGlobalMetricsFile, containerMetricsFilePath, filteredIoMetricsFile,
                    resultsFolder + "/" + outputFileName);
        } catch (IOException e) {
            logger.error("Error processing monitoring data in directory: " + dirName, e);
        }
    }

    private void filterGlobalMetrics(String globalMetricsFile, String outputFile, String containerID) {

        List<String> lines;

        try {
            lines = Files.readAllLines(Paths.get(globalMetricsFile));
            if (lines.isEmpty()) {
                return;
            }
        } catch (IOException e) {
            logger.error("Error reading global metrics file for containerID='" + containerID + "'.", e);
            return;
        }

        String[] headers = lines.get(0).split(",");
        List<String> filteredHeaders = Arrays.stream(headers)
                .filter(h -> h.contains(containerID) || h.equals("Time"))
                .collect(Collectors.toList());

        List<String> outputLines = new ArrayList<>();
        List<String> outputHeaders = new ArrayList<>();
        outputHeaders.add("Time");
        outputHeaders.add("memory_usage");
        filteredHeaders.stream().filter(h -> h.contains("cpu"))
                .forEach(h -> outputHeaders.add(h));
        outputLines.add(String.join(",", outputHeaders));
        logger.info("Filtered Global Headers: " + outputHeaders);

        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split(",", -1);
            Map<String, String> rowMap = new HashMap<>();
            for (int j = 0; j < headers.length; j++) {
                rowMap.put(headers[j], values[j]);
            }

            // Handle missing fields
            for (String h : filteredHeaders) {
                rowMap.putIfAbsent(h, "0");
            }

            // Convert CPU
            for (String h : filteredHeaders) {
                if (h.contains("cgroup.cpu.stat") && rowMap.get(h) != null && !rowMap.get(h).isEmpty()) {
                    DecimalFormat df = new DecimalFormat("#.###");
                    double cpu_period = Double.parseDouble(df.format(Double.parseDouble(rowMap.get(h)) / 10000.0));
                    rowMap.put(h, String.valueOf(cpu_period));
                }
            }

            // Calculate memory_usage (current - inactive_file)
            double mem = 0.0;
            String current = filteredHeaders.stream().filter(f -> f.contains("cgroup.memory.current")).findFirst()
                    .orElse(null);
            String inactive = filteredHeaders.stream().filter(f -> f.contains("cgroup.memory.stat.inactive_file"))
                    .findFirst().orElse(null);

            if (current != null && !rowMap.get(current).isEmpty()) {
                mem += Double.parseDouble(rowMap.get(current));
            }
            if (inactive != null && !rowMap.get(inactive).isEmpty()) {
                mem -= Double.parseDouble(rowMap.get(inactive));
            }
            rowMap.put("memory_usage", String.valueOf(mem));

            List<String> ordered = new ArrayList<>();
            for (String h : outputHeaders) {
                ordered.add(rowMap.get(h));
            }
            outputLines.add(String.join(",", ordered));
        }

        try {
            Files.write(Paths.get(outputFile), outputLines);
        } catch (IOException e) {
            logger.error("Error writing filtered global metrics for containerID='" + containerID + "'.", e);
        }
    }

    private void filterIoMetrics(String ioMetricsFile, String outputFile, String containerID) {

        List<String> lines;

        try {
            lines = Files.readAllLines(Paths.get(ioMetricsFile));
            if (lines.isEmpty()) {
                return;
            }
        } catch (IOException e) {
            logger.error("Error reading IO metrics file for containerID='" + containerID + "'.", e);
            return;
        }

        String[] headers = lines.get(0).split(",");
        List<String> filteredHeaders = Arrays.stream(headers)
                .filter(h -> h.contains(containerID) || h.equals("Time"))
                .collect(Collectors.toList());

        List<String> outputLines = new ArrayList<>();
        List<String> outputHeaders = new ArrayList<>();
        outputHeaders.add("Time");
        filteredHeaders.stream().filter(h -> h.contains("cgroup.io"))
                .forEach(h -> outputHeaders.add(h));
        outputLines.add(String.join(",", outputHeaders));
        logger.info("Filtered IO Headers: " + outputHeaders);

        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split(",", -1);
            Map<String, String> rowMap = new HashMap<>();
            for (int j = 0; j < headers.length; j++) {
                rowMap.put(headers[j], values[j]);
            }

            // Handle missing fields
            for (String h : filteredHeaders) {
                rowMap.putIfAbsent(h, "0");
            }

            List<String> ordered = new ArrayList<>();
            for (String h : outputHeaders) {
                ordered.add(rowMap.get(h));
            }
            outputLines.add(String.join(",", ordered));
        }

        try {
            Files.write(Paths.get(outputFile), outputLines);
        } catch (IOException e) {
            logger.error("Error writing filtered IO metrics for containerID='" + containerID + "'.", e);
        }
    }

    private List<String> buildOrderedHeader(List<String> combinedHeader) {
        List<String> ordered = new ArrayList<>();
        ordered.add("Time");

        // network IN
        combinedHeader.stream()
                .filter(s -> s.contains("network.interface.in.bytes"))
                .sorted()
                .forEach(ordered::add);

        // network OUT
        combinedHeader.stream()
                .filter(s -> s.contains("network.interface.out.bytes"))
                .sorted()
                .forEach(ordered::add);

        // memory (computed field)
        combinedHeader.stream()
                .filter(s -> s.contains("memory_usage"))
                .forEach(ordered::add);

        // cpu
        combinedHeader.stream()
                .filter(s -> s.contains("cgroup.cpu.stat.system"))
                .forEach(ordered::add);

        combinedHeader.stream()
                .filter(s -> s.contains("cgroup.cpu.stat.user"))
                .forEach(ordered::add);

        // disk
        combinedHeader.stream()
                .filter(s -> s.contains("cgroup.io.stat.rbytes"))
                .forEach(ordered::add);

        combinedHeader.stream()
                .filter(s -> s.contains("cgroup.io.stat.wbytes"))
                .forEach(ordered::add);

        return ordered;
    }

    private List<String> reorderRow(List<String> row, List<String> combinedHeader, List<String> orderedHeader) {
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < combinedHeader.size(); i++) {
            indexMap.put(combinedHeader.get(i), i);
        }

        List<String> reordered = new ArrayList<>();
        for (String col : orderedHeader) {
            Integer idx = indexMap.get(col);
            if (idx != null) {
                reordered.add(row.get(idx));
            } else {
                reordered.add("0"); // Default value if column missing
            }
        }
        return reordered;
    }

    private String[] findNearest(LocalDateTime targetTime, List<String[]> rows, DateTimeFormatter fmt,
            long toleranceMillis) {
        String[] nearest = null;
        long minDiff = Long.MAX_VALUE;
        boolean found = false;

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);

            String timeStr = row[0].replace("\"", "");
            LocalDateTime rowTime = LocalDateTime.parse(timeStr, fmt);

            long diff = Math.abs(Duration.between(targetTime, rowTime).toMillis());

            if (!found && diff > toleranceMillis) {
                continue;
            }
            if (diff <= toleranceMillis) {
                found = true;

                if (diff < minDiff) {
                    minDiff = diff;
                    nearest = row;
                } else {
                    break;
                }
            }
            if (found && rowTime.isAfter(targetTime) && diff > minDiff) {
                break;
            }
        }

        return nearest;
    }

    private void joinMetrics(String filteredGlobalMetricsFile, String containerMetricsFile,
            String filteredIoMetricsFile, String outputFile) {
        List<String[]> globalRows;
        List<String[]> containerRows;
        List<String[]> ioRows;

        try {
            globalRows = Files.lines(Paths.get(filteredGlobalMetricsFile))
                    .map(l -> l.split(",", -1))
                    .collect(Collectors.toList());
            containerRows = Files.lines(Paths.get(containerMetricsFile))
                    .map(l -> l.split(",", -1))
                    .collect(Collectors.toList());
            ioRows = Files.lines(Paths.get(filteredIoMetricsFile))
                    .map(l -> l.split(",", -1))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error reading metrics files for joining.", e);
            return;
        }

        if (globalRows.isEmpty() || containerRows.isEmpty() || ioRows.isEmpty()) {
            logger.error("One or more metric files are empty - global: " + globalRows.isEmpty() +
                    ", container: " + containerRows.isEmpty() + ", io: " + ioRows.isEmpty());
            return;
        }

        String[] globalHeader = globalRows.get(0);
        String[] containerHeader = containerRows.get(0);
        String[] ioHeader = ioRows.get(0);

        List<String> outputLines = new ArrayList<>();

        // Combined header
        List<String> combinedHeader = new ArrayList<>();
        combinedHeader.add("Time");

        // Add container headers (network)
        for (int i = 1; i < containerHeader.length; i++) {
            combinedHeader.add(containerHeader[i]);
        }

        // Add global headers (memory, cpu)
        for (int i = 1; i < globalHeader.length; i++) {
            combinedHeader.add(globalHeader[i]);
        }

        // Add IO headers
        for (int i = 1; i < ioHeader.length; i++) {
            combinedHeader.add(ioHeader[i]);
        }

        List<String> orderedHeader = buildOrderedHeader(combinedHeader);
        outputLines.add(String.join(",", orderedHeader));
        logger.info("Final Output Headers: " + orderedHeader);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

        for (int i = 1; i < containerRows.size(); i++) {
            String[] containerRow = containerRows.get(i);

            String containerTimeStr = containerRow[0].replace("\"", "");
            LocalDateTime containerTime = LocalDateTime.parse(containerTimeStr, fmt);

            // Find nearest memory and cpu entry within 0.5sec tolerance
            String[] nearestGlobal = findNearest(containerTime, globalRows, fmt, 500);
            if (nearestGlobal != null) {
                globalRows.remove(nearestGlobal);
            }

            // Find nearest IO entry within 0.5sec tolerance
            String[] nearestIO = findNearest(containerTime, ioRows, fmt, 500);
            if (nearestIO != null) {
                ioRows.remove(nearestIO);
            }

            if (nearestGlobal != null && nearestIO != null) {
                List<String> mergedRow = new ArrayList<>(Arrays.asList(containerRow));

                // Add global metrics (skip Time column)
                mergedRow.addAll(Arrays.asList(nearestGlobal).subList(1, nearestGlobal.length));

                // Add IO metrics (skip Time column)
                mergedRow.addAll(Arrays.asList(nearestIO).subList(1, nearestIO.length));

                // Quote Time
                mergedRow.set(0, "\"" + mergedRow.get(0).replace("\"", "") + "\"");

                List<String> reorderedRow = reorderRow(mergedRow, combinedHeader, orderedHeader);
                outputLines.add(String.join(",", reorderedRow));
            }
        }

        try {
            Files.write(Paths.get(outputFile), outputLines);
            logger.info("Successfully wrote merged metrics to: " + outputFile);
        } catch (IOException e) {
            logger.error("Error writing joined metrics to file: " + outputFile, e);
        }
    }
}