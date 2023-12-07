package pt.haslab.mulletbench.utils;

import pt.haslab.mulletbench.WorkloadType;

public class ClientOptions {

    public String name;
    public String address;
    public WorkloadType type;
    public String target;
    public boolean monitor;

    @Override
    public String toString() {
        return "ClientOptions{" +
            "name='" + name + '\'' +
            ", address='" + address + '\'' +
            ", target='" + target + '\'' +
            '}';
    }
}
