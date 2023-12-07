package pt.haslab.mulletbench;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.haslab.mulletbench.stats.StatsCollector;



public class Client implements Runnable {

    private static final Logger logger = LogManager.getLogger();

    enum Status {
        UNKNOWN,
        READY,
        RUNNING,
        FINISHED,
    }

    public final String name;
    public final InetAddress address;
    private Status status;
    private Socket socket;
    private ObjectInputStream objIn;
    private ObjectOutputStream objOut;

    public DatabaseNode node;
    private WorkloadType type;

    private final boolean monitor;

    public StatsCollector statsCollector; //received after execution


    public Client(String name, InetAddress address, DatabaseNode node, WorkloadType type, boolean monitor) {
        this.name = name;
        this.address = address;
        this.status = Status.UNKNOWN;
        this.node = node;
        this.type = type;
        this.monitor = monitor;
    }

    public void close() throws IOException{
        if(this.socket.isConnected()){
            this.socket.close();
        }
    }

    public String getName() {
        return this.name;
    }

    public boolean monitor(){
        return monitor;
    }

    public void connect(Socket socket, ObjectInputStream objIn) throws IOException{
        this.status = Status.READY;
        this.socket = socket;
        this.objOut = new ObjectOutputStream(socket.getOutputStream());
        this.objIn = objIn;
    }

    public void displayResults(){
        if(this.status == Status.FINISHED){
            statsCollector.printStats();
        } else {
            logger.error("Cannot displayResults, client not finished");
        }
    }

    @Override
    public void run() {
        if(!this.status.equals(Status.READY)){
            logger.error("Client " + this.address.toString() + " not connected");
            return;
        }

        // start clients
        // send message to open sockets
        try{
            logger.info("Sending start");
            this.objOut.writeObject(SyncMessage.START);
            this.objOut.flush();
            this.status = Status.RUNNING;
            logger.debug("Start sent");
        } catch (IOException e){
            e.printStackTrace();
        }

        // wait for results from clients

        if(this.status == Status.RUNNING){
            try{
                logger.info("Waiting for results from client");
                this.statsCollector = (StatsCollector) objIn.readObject(); // read results
                logger.info("Results from client received");
                this.status = Status.FINISHED;
                close();
            } catch (IOException e){
                logger.error("Failed to receive results");
                e.printStackTrace();
            } catch (ClassNotFoundException e){
                logger.error("Received class not found");
                e.printStackTrace();
            }

        }

    }

}
