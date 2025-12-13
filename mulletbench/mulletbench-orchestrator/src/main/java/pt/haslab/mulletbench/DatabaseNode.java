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
    private final String containerID;
    private final String cgroupsVersion;

    public DatabaseNode(String name, Layer layer, InetAddress address, String containerID, String cgroupsVersion, boolean monitor) {
        this.name = name;
        this.layer = layer;
        this.address = address;
        this.containerID = containerID;
        this.cgroupsVersion = cgroupsVersion;
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

    public String getContainerID() {
        return containerID;
    }

    public String getCgroupsVersion() {
        return cgroupsVersion;
    }
}
