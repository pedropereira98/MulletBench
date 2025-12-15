package pt.haslab.mulletbench;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.utils.ClientOptions;
import pt.haslab.mulletbench.utils.GlobalStats;
import pt.haslab.mulletbench.utils.NodeOptions;
import pt.haslab.mulletbench.utils.OrchestratorOptions;
import pt.haslab.mulletbench.utils.StageOptions;

public class Orchestrator {

    private static final Logger logger = LogManager.getLogger();
    private static final int MAX_CLIENT_CONNECTION_TRIES = 5;
    private final MonitoringController monitoringController;

    String database;
    Map<String, DatabaseNode> nodes = new HashMap<>();

    ServerSocket serverSocket;
    GlobalStats globalStats;

    Map<InetAddress, ClientAddress> clientAddresses = new HashMap<>();
    List<Client> clients = new ArrayList<>();

    List<Stage> stages = new ArrayList<>();

    String resultsFolder;
    private final short finalMonitoringPeriodS;

    public Orchestrator(OrchestratorOptions options) throws IOException {
        this.database = options.database;
        this.resultsFolder = options.resultsFolder + "/run-" + System.currentTimeMillis();
        logger.info("Results folder: " + resultsFolder);
        this.monitoringController = new MonitoringController(options.monitoringInterval, this.resultsFolder,
                options.clientContainerBase);

        for (NodeOptions optionsNode : options.nodes) {
            InetAddress address = InetAddress.getByName(optionsNode.address);
            DatabaseNode node = new DatabaseNode(optionsNode.name, optionsNode.layer, address, optionsNode.containerID,
                    optionsNode.cgroupsVersion, optionsNode.monitor);
            nodes.put(optionsNode.name, node);
        }

        for (ClientOptions clientOptions : options.clients) {
            InetAddress address = InetAddress.getByName(clientOptions.address);
            DatabaseNode dbNode = nodes.get(clientOptions.target);
            Client c = new Client(clientOptions.name, address, dbNode, clientOptions.type, clientOptions.monitor);

            ClientAddress clientAddress;

            if (!clientAddresses.containsKey(address)) {
                clientAddress = new ClientAddress(address);
                clientAddresses.put(address, clientAddress);
            } else {
                clientAddress = clientAddresses.get(address);
            }

            clientAddress.addClient(c);
            clients.add(c);
        }

        this.serverSocket = new ServerSocket(27205);
        this.globalStats = new GlobalStats();

        for (StageOptions stageOptions : options.stages) {
            this.stages.add(new Stage(stageOptions.clients));
        }

        this.finalMonitoringPeriodS = options.finalMonitoringPeriodS;
    }

    private void aggregateResults() {
        for (Client c : this.clients) {
            this.globalStats.joinCollector(c.statsCollector);
        }
    }

    private void displayResults() {
        // per node, per client, per worker?

        int indentation = 0;
        System.out.println("\n\nTest results:");
        indentation++;

        for (Client c : clients) {
            if (c.statsCollector.type == WorkloadType.QUERY) {
                System.out.println("\n" + IndentString.indent(indentation) + "Client " + c.name + " query seed: "
                        + c.statsCollector.querySeed);
            }
        }

        int i = 1;

        System.out.println("\n" + IndentString.indent(indentation) + "Stats per stage:");
        indentation++;
        for (Stage stage : stages) {
            List<Client> stageClients = clients.stream().filter(client -> stage.clients().contains(client.name))
                    .toList();

            GlobalStats stageStats = new GlobalStats();
            stageClients.forEach(client -> stageStats.joinCollector(client.statsCollector));
            System.out.println("\n" + IndentString.indent(indentation) + "Stage " + i + " stats:");
            indentation++;
            final int stageIndentation = indentation;
            stageClients.forEach((c) -> {
                System.out.println("\n" + IndentString.indent(stageIndentation) + "Client " + c.name + " stats:");
                c.displayResults(stageIndentation + 1);
            });

            System.out.println("\n" + IndentString.indent(indentation) + "Joined Stage " + i + " stats:");
            indentation++;
            stageStats.printStats(indentation);
            indentation--;
            indentation--;
            i++;
        }
        indentation--;

        System.out.println("\n" + IndentString.indent(indentation) + "Stats per node:");
        indentation++;
        for (DatabaseNode node : nodes.values()) {
            GlobalStats nodeStats = new GlobalStats();
            clients.stream().filter(client -> client.node.getName().equals(node.getName()))
                    .forEach(client -> nodeStats.joinCollector(client.statsCollector));

            System.out.println("\n" + IndentString.indent(indentation) + node.getName() + " stats:");
            indentation++;
            nodeStats.printStats(indentation);
            indentation--;
        }
        indentation--;

        System.out.println("\n" + IndentString.indent(indentation) + "Stats per layer:");
        indentation++;
        System.out.println("\n" + IndentString.indent(indentation) + "Edge database node stats:");
        indentation++;
        GlobalStats edgeStats = new GlobalStats();
        clients.stream().filter(client -> client.node.layer.equals(DatabaseNode.Layer.EDGE))
                .forEach(client -> edgeStats.joinCollector(client.statsCollector));
        edgeStats.printStats(indentation);
        indentation--;

        System.out.println("\n" + IndentString.indent(indentation) + "Cloud database node stats:");
        indentation++;
        GlobalStats cloudStats = new GlobalStats();
        clients.stream().filter(client -> client.node.layer.equals(DatabaseNode.Layer.CLOUD))
                .forEach(client -> cloudStats.joinCollector(client.statsCollector));
        cloudStats.printStats(indentation);
        indentation--;
        indentation--;

        System.out.println("\n" + IndentString.indent(indentation) + "Global stats:");
        indentation++;
        globalStats.printStats(indentation);
        indentation--;
    }

    private void startMonitoringDatabase() {
        for (DatabaseNode node : nodes.values()) {
            if (node.monitor()) {
                monitoringController.monitorDatabase(database + "-" + node.getName(), node.getContainerID(),
                        node.getCgroupsVersion(), node.getName() + ".csv", node.getAddress().toString().split("/")[1]);
            }
        }
    }

    private void startMonitoring(List<String> stageClientIDs) {
        // run script for each database node
        // pmrep -t 0.5sec -b MBytes -I --container iotdb-edge1
        // network.interface.in.bytes network.interface.out.bytes cgroup.memory.usage
        // cgroup.cpuacct.stat.system cgroup.cpuacct.stat.user
        // cgroup.blkio.all.throttle.io_service_bytes.read
        // cgroup.blkio.all.throttle.io_service_bytes.write -o csv -f "%Y-%m-%d
        // %H:%M:%S.%f"
        for (ClientAddress clientAddress : clientAddresses.values()) {
            for (Client client : clientAddress.getClients()) {
                if (client.monitor() && stageClientIDs.contains(client.name)) {
                    String clientName = client.getName();
                    String clientContainerID = client.getContainerID();
                    String clientCgroupsVersion = client.getCgroupsVersion();
                    monitoringController.monitorClient(clientName, clientContainerID, clientCgroupsVersion,
                            clientName + ".csv", clientAddress.getAddress().toString().split("/")[1]);
                }
            }
        }
    }

    private void saveToCSV(String directoryPath) {

        for (Client c : this.clients) {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(new File(directoryPath, c.name + ".csv")));
                for (String str : c.statsCollector.toCSV()) {
                    writer.write(str + System.lineSeparator());
                }
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void run() {
        // open server socket
        // wait for connections from all clients
        int tries = 0;

        while (tries < MAX_CLIENT_CONNECTION_TRIES) {

            try {
                int receivedClients = 0;
                serverSocket.setSoTimeout(120000); // set 2 minute timeout for accepting connections

                // creates folder for results
                Files.createDirectories(Paths.get(this.resultsFolder));
                logger.info("Waiting for clients");
                while (receivedClients < clients.size()) {
                    Socket receivedSocket = serverSocket.accept();
                    InetAddress socketAddress = receivedSocket.getInetAddress();
                    logger.info("Received connection request from " + socketAddress.toString());

                    ObjectInputStream clientObjIn = new ObjectInputStream(
                            new BufferedInputStream(receivedSocket.getInputStream()));
                    String receivedMessage = (String) clientObjIn.readObject();
                    String[] parts = receivedMessage.split(";");

                    if (parts.length < 4) {
                        logger.error("Received invalid message from " + socketAddress);
                        continue;
                    }

                    String clientID = parts[0];
                    String clientAddress = parts[1];
                    String containerID = parts[2];
                    String cgroupsVersion = parts[3];
                    InetAddress clientInetAddress = InetAddress.getByName(clientAddress);

                    if (clientAddresses.containsKey(clientInetAddress)
                            && clientAddresses.get(clientInetAddress).containsClient(clientID)) {
                        logger.debug("Adding " + clientInetAddress);
                        logger.info("Received " + clientID);

                        ClientAddress ca = clientAddresses.get(clientInetAddress);

                        if (ca.connect(clientID, receivedSocket, clientObjIn)) {
                            receivedClients++;
                            Client client = ca.getClient(clientID);
                            client.setContainerID(containerID);
                            client.setCgroupsVersion(cgroupsVersion);
                        } else {
                            logger.error("Address already received all clients");
                        }

                    } else {
                        logger.error("Received unexpected connection with socketAddress " + socketAddress
                                + " and clientAddress " + clientInetAddress);
                    }
                }
            } catch (SocketTimeoutException e) {
                logger.error("Timeout while waiting for clients to connect");
                tries++;
                if (tries < 5) {
                    logger.info("Retrying to receive clients (" + tries + "/5)");
                    continue;
                }
                throw new RuntimeException("Timeout while waiting for clients to connect");
            } catch (IOException e) {
                logger.error("Connection to a client failed");
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        // start resource metric collection
        startMonitoringDatabase();

        for (Stage stage : stages) {
            logger.info("Starting stage");
            startMonitoring(stage.clients());

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            logger.info("Starting client threads");
            for (ClientAddress ca : clientAddresses.values()) {
                ca.startThreads(stage);
            }
            logger.info("All clients started");
            try {
                for (ClientAddress ca : clientAddresses.values()) {
                    ca.joinThreads(stage);
                }
            } catch (InterruptedException e) {
                logger.error("Failed to join clientThreads");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // stop resource metric collection
            monitoringController.stopMonitoringClients();
        }

        try {
            Thread.sleep(finalMonitoringPeriodS * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);

        }

        monitoringController.stopMonitoringNodes();

        // aggregate results
        aggregateResults();

        // display results
        displayResults();

        saveToCSV(resultsFolder);

        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.error("Failed to close ServerSocket");
        }
    }
}
