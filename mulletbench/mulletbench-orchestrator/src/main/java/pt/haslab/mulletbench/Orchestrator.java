package pt.haslab.mulletbench;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.haslab.mulletbench.utils.*;

public class Orchestrator {

    private static final Logger logger = LogManager.getLogger();
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

    public Orchestrator(OrchestratorOptions options) throws IOException{
        this.database = options.database;
        this.resultsFolder = options.resultsFolder + "/run-" + System.currentTimeMillis();
        logger.info("Results folder: " + resultsFolder);
        this.monitoringController = new MonitoringController(options.monitoringInterval, this.resultsFolder, options.clientContainerBase);

        for(NodeOptions optionsNode: options.nodes){
            InetAddress address = InetAddress.getByName(optionsNode.address);
            DatabaseNode node = new DatabaseNode(optionsNode.name, optionsNode.layer, address, optionsNode.monitor);
            nodes.put(optionsNode.name, node);
        }

        for(ClientOptions clientOptions: options.clients){
            InetAddress address = InetAddress.getByName(clientOptions.address);
            DatabaseNode dbNode = nodes.get(clientOptions.target);
            Client c = new Client(clientOptions.name, address, dbNode, clientOptions.type, clientOptions.monitor);

            ClientAddress clientAddress;

            if(!clientAddresses.containsKey(address)){
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

        for(StageOptions stageOptions: options.stages){
            this.stages.add(new Stage(stageOptions.clients));
        }

        this.finalMonitoringPeriodS = options.finalMonitoringPeriodS;
    }

    private void aggregateResults(){
        for(Client c: this.clients){
            this.globalStats.joinCollector(c.statsCollector);
        }
    }

    private void displayResults(){
        // per node, per client, per worker?

        int i = 1;
        System.out.println("\nTest results:");

        System.out.println("\nStats per stage:");
        for(Stage stage: stages){
            List<Client> stageClients = clients.stream().filter(client -> stage.clients().contains(client.name)).toList();

            GlobalStats stageStats = new GlobalStats();
            stageClients.forEach(Client::displayResults);
            stageClients.forEach(client -> stageStats.joinCollector(client.statsCollector));

            System.out.println("\nStage " + i + " stats:");
            stageStats.printStats();

            i++;
        }

        System.out.println("\nStats per node:");
        for(DatabaseNode node: nodes.values()){
            GlobalStats nodeStats = new GlobalStats();
            clients.stream().filter(client -> client.node.getName().equals(node.getName())).forEach(client -> nodeStats.joinCollector(client.statsCollector));

            System.out.println("\n" + node.getName() + " stats:");
            nodeStats.printStats();
        }

        System.out.println("\nStats per layer:");
        System.out.println("\nEdge database node stats:");
        GlobalStats edgeStats = new GlobalStats();
        clients.stream().filter(client -> client.node.layer.equals(DatabaseNode.Layer.EDGE)).forEach(client -> edgeStats.joinCollector(client.statsCollector));
        edgeStats.printStats();

        System.out.println("\nCloud database node stats:");
        GlobalStats cloudStats = new GlobalStats();
        clients.stream().filter(client -> client.node.layer.equals(DatabaseNode.Layer.CLOUD)).forEach(client -> cloudStats.joinCollector(client.statsCollector));
        cloudStats.printStats();

        System.out.println("\nGlobal stats:");

        globalStats.printStats();
    }

    private void startMonitoringDatabase(){
        for(DatabaseNode node: nodes.values()) {
            if (node.monitor()){
                monitoringController.monitorDatabase(database + "-" + node.getName(), node.getName() + ".csv", node.getAddress().toString().split("/")[1]);
            }
        }
    }

    private void startMonitoring(List<String> stageClientIDs){
        // run script for each database node
        // pmrep -t 0.5sec -b MBytes -I --container iotdb-edge1 network.interface.in.bytes network.interface.out.bytes cgroup.memory.usage cgroup.cpuacct.stat.system cgroup.cpuacct.stat.user cgroup.blkio.all.throttle.io_service_bytes.read cgroup.blkio.all.throttle.io_service_bytes.write -o csv -f "%Y-%m-%d %H:%M:%S.%f"
        for(ClientAddress clientAddress: clientAddresses.values()){
            for(Client client: clientAddress.getClients()){
                if (client.monitor() && stageClientIDs.contains(client.name)){
                    String clientName = client.getName();
                    monitoringController.monitorClient(clientName, clientName + ".csv", clientAddress.getAddress().toString().split("/")[1]);
                }
            }
        }
    }
    private void saveToCSV(String directoryPath){

        for(Client c: this.clients){
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(new File(directoryPath, c.name + ".csv")));
                for(String str: c.statsCollector.toCSV()){
                    writer.write(str + System.lineSeparator());
                }
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void run(){
        // open server socket
        // wait for connections from all clients
        try{
            int receivedClients = 0;

            //creates folder for results
            Files.createDirectories(Paths.get(this.resultsFolder));
            logger.info("Waiting for clients");
            while(receivedClients < clients.size()){
                Socket receivedSocket = serverSocket.accept(); //TODO timeout if not all connections are received
                InetAddress socketAddress = receivedSocket.getInetAddress();
                logger.info("Accepted " + socketAddress.toString());

                if(clientAddresses.containsKey(socketAddress)){
                    logger.debug("Adding " + socketAddress);

                    ObjectInputStream clientObjIn = new ObjectInputStream(new BufferedInputStream(receivedSocket.getInputStream()));
                    String clientID = (String) clientObjIn.readObject();
                    logger.info("Received " + clientID);

                    if(clientAddresses.get(socketAddress).connect(clientID, receivedSocket, clientObjIn)){
                        receivedClients++;
                    } else {
                        logger.error("Address already received all clients");
                    }
                } else {
                    logger.error("Received unexpected connection " + socketAddress);
                }
            }
        } catch (IOException e){
            logger.error("Connection to a client failed");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // start resource metric collection
        startMonitoringDatabase();

        for(Stage stage: stages){
            logger.info("Starting stage");
            startMonitoring(stage.clients());

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            logger.info("Starting client threads");
            for(ClientAddress ca: clientAddresses.values()){
                ca.startThreads(stage);
            }
            logger.info("All clients started");
            try{
                for(ClientAddress ca: clientAddresses.values()){
                    ca.joinThreads(stage);
                }
            } catch (InterruptedException e){
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

        try{
            Thread.sleep(finalMonitoringPeriodS * 1000);
        } catch (InterruptedException e){
            throw new RuntimeException(e);

        }

        monitoringController.stopMonitoringNodes();

        // aggregate results
        aggregateResults();

        // display results
        displayResults();

        saveToCSV(resultsFolder);

        try{
            serverSocket.close();
        } catch(IOException e){
            logger.error("Failed to close ServerSocket");
        }
    }
}
