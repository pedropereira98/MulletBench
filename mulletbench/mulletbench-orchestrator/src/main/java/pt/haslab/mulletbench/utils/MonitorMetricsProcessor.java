package pt.haslab.mulletbench.utils;

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
            // String containerName = parts[0];
            String containerID = parts[1];

            // get all files in the directory
            List<String> files = Files.list(dirPath).map(Path::getFileName).map(Path::toString).toList();

            String globalMetricsFile = "";
            String containerMetricsFile = "";
            for (String file : files) {
                if (file.startsWith("global-")) {
                    globalMetricsFile = file;
                } else if (file.startsWith("container-")) {
                    containerMetricsFile = file;
                }
            }

            if (globalMetricsFile.isEmpty() || containerMetricsFile.isEmpty()) {
                logger.warn("Missing monitoring files in directory: " + dirName);
                return;
            }

            String globalMetricsFilePath = dirPath + "/" + globalMetricsFile;
            String containerMetricsFilePath = dirPath + "/" + containerMetricsFile;

            String[] auxParts = globalMetricsFile.split("-");

            if (auxParts.length < 2) {
                logger.warn("Unexpected container input file format: " + globalMetricsFile);
                return;
            }

            String outputFileName = "monitor-" + auxParts[1];
            String filteredGlobalMetricsFile = dirPath + "/global-filtered-" + outputFileName;

            filterGlobalMetrics(globalMetricsFilePath, filteredGlobalMetricsFile, containerID);
            joinMetrics(filteredGlobalMetricsFile, containerMetricsFilePath, resultsFolder + "/" + outputFileName);
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
                .filter(h -> h.contains(containerID) || h.equals("Time") || h.equals("Timestamp")
                        || h.equals("Interval"))
                .collect(Collectors.toList());

        List<String> outputLines = new ArrayList<>();
        List<String> outputHeaders = new ArrayList<>();
        outputHeaders.add("Time");
        outputHeaders.add("memory_usage");
        filteredHeaders.stream().filter(h -> h.contains("cpu")).forEach(h -> outputHeaders.add(h));
        outputLines.add(String.join(",", outputHeaders));

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
                    double cpu_period = Double.parseDouble(rowMap.get(h)) / 10000.0;
                    rowMap.put(h, String.valueOf(cpu_period));
                }
            }

            // Calculate memory_usage (current - inactive_file)
            double mem = 0.0;
            String current = filteredHeaders.stream().filter(f -> f.contains("cgroup.memory.current")).findFirst().orElse(null);
            String inactive = filteredHeaders.stream().filter(f -> f.contains("cgroup.memory.stat.inactive_file")).findFirst().orElse(null);

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
            return;
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
                .filter(s -> s.contains("disk.dev.read_bytes"))
                .forEach(ordered::add);

        combinedHeader.stream()
                .filter(s -> s.contains("disk.dev.write_bytes"))
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
            reordered.add(row.get(indexMap.get(col)));
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

    private void joinMetrics(String filteredGlobalMetricsFile, String containerMetricsFile, String outputFile) {
        List<String[]> globalRows;
        List<String[]> containerRows;

        try {
            globalRows = Files.lines(Paths.get(filteredGlobalMetricsFile))
                    .map(l -> l.split(",", -1))
                    .collect(Collectors.toList());
            containerRows = Files.lines(Paths.get(containerMetricsFile))
                    .map(l -> l.split(",", -1))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error reading metrics files for joining.", e);
            return;
        }

        if (globalRows.isEmpty() || containerRows.isEmpty()) {
            return;
        }

        String[] globalHeader = globalRows.get(0);
        String[] containerHeader = containerRows.get(0);

        List<String> outputLines = new ArrayList<>();

        // Combined header
        List<String> combinedHeader = new ArrayList<>();
        combinedHeader.add("Time");
        for (int i = 1; i < containerHeader.length; i++) {
            combinedHeader.add(containerHeader[i]);
        }
        for (int i = 1; i < globalHeader.length; i++) {
            combinedHeader.add(globalHeader[i]);
        }

        List<String> orderedHeader = buildOrderedHeader(combinedHeader);
        outputLines.add(String.join(",", orderedHeader));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
        for (int i = 1; i < containerRows.size(); i++) {
            String[] containerRow = containerRows.get(i);

            String containerTimeStr = containerRow[0].replace("\"", "");
            LocalDateTime containerTime = LocalDateTime.parse(containerTimeStr, fmt);

            // Find nearest memory and cpu entry within 0.5sec tolerance
            String[] nearestGlobal = findNearest(containerTime, globalRows, fmt, 500); // 0.5s
            globalRows.remove(nearestGlobal);

            if (nearestGlobal != null) {
                List<String> mergedRow = new ArrayList<>(Arrays.asList(containerRow));
                mergedRow.addAll(Arrays.asList(nearestGlobal).subList(1, nearestGlobal.length));

                // Quote Time
                mergedRow.set(0, "\"" + mergedRow.get(0).replace("\"", "") + "\"");

                List<String> reorderedRow = reorderRow(mergedRow, combinedHeader, orderedHeader);
                outputLines.add(String.join(",", reorderedRow));
            }
        }

        try {
            Files.write(Paths.get(outputFile), outputLines);
        } catch (IOException e) {
            logger.error("Error writing joined metrics to file: " + outputFile, e);
        }
    }
}