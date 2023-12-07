package pt.haslab.mulletbench;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ClientAddress {
    private InetAddress address;

    private Map<String, Client> clients = new HashMap<>();
    private Map<String, Thread> clientThreads = new HashMap<>();

    private int acceptedClients = 0;

    public ClientAddress(InetAddress address) {
        this.address = address;
    }

    public boolean connect(String clientID, Socket socket, ObjectInputStream clientObjIn) throws IOException{
        if(acceptedClients >= clients.size()){
            return false;
        }
        clients.get(clientID).connect(socket, clientObjIn);
        acceptedClients++;
        return true;
    }

    public void addClient(Client c){
        this.clients.put(c.getName(), c);
        this.clientThreads.put(c.getName(), new Thread(c));
    }

    public InetAddress getAddress(){
        return address;
    }

    public Collection<Client> getClients(){
        return clients.values();
    }

    public int numberOfClients(){
        return clients.size();
    }

    public void startThreads(Stage stage){
        for(String clientID: stage.clients()){
            if(clientThreads.containsKey(clientID)){
                clientThreads.get(clientID).start();
            }
        }
    }

    public void joinThreads(Stage stage) throws InterruptedException {
        for(String clientID: stage.clients()){
            if(clientThreads.containsKey(clientID)){
                clientThreads.get(clientID).join();
            }
        }
    }
}
