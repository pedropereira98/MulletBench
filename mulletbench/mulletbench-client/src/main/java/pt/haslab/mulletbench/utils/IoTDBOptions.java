package pt.haslab.mulletbench.utils;

public class IoTDBOptions {
    public String host = "192.168.112.45";
    public String port = "6667";
    public String username = "root";
    public String password = "root";
    public String devicePath = "root.watch.accel.d1";
    public boolean queryAlignByDevice = true;

    public IoTDBOptions() {
    }


    public IoTDBOptions(String host, String port, String username, String password, String devicePath) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.devicePath = devicePath;
    }

    @Override
    public String toString() {
        return "IoTDBOptions{" +
            "host='" + host + '\'' +
            ", port='" + port + '\'' +
            ", username='" + username + '\'' +
            ", password='" + password + '\'' +
            ", devicePath='" + devicePath + '\'' +
            ", queryAlignByDevice=" + queryAlignByDevice +
            '}';
    }
}
