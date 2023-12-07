package pt.haslab.mulletbench;

import java.net.InetAddress;

public class DatabaseNode {

    public enum Layer {
        EDGE,
        CLOUD
    }

    private final String name;
    public final Layer layer;
    private final InetAddress address;
    private final boolean monitor;

    public DatabaseNode(String name, Layer layer, InetAddress address, boolean monitor) {
        this.name = name;
        this.layer = layer;
        this.address = address;
        this.monitor = monitor;
    }

    public String getName() {
        return name;
    }

    public InetAddress getAddress() {
        return address;
    }

    public boolean monitor(){
        return monitor;
    }
}
