package pt.haslab.mulletbench.utils;

import pt.haslab.mulletbench.WorkloadType;

public class ClientOptions {

    public String name;
    public String address;
    public WorkloadType type;
    public String target;
    public boolean monitor;
    public String containerID="";
    public String cgroupsVersion="";
    public long querySeed = -1L;

    @Override
    public String toString() {
        return "ClientOptions{" +
            "name='" + name + '\'' +
            ", address='" + address + '\'' +
            ", target='" + target + '\'' +
            ", type=" + type +
            ((type == WorkloadType.QUERY) ?", querySeed=" + ((querySeed == -1L) ?"\'not specified\'" :querySeed) :"") +
            '}';
    }
}
