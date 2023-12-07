package pt.haslab.mulletbench.utils;

public class InfluxDBOptions {
    public String serverURL;
    public String token;
    public String orgID;
    public String bucket;
    public int writeTimeout = 10; //write timeout in seconds
    public int readTimeout = 10; //read timeout in seconds

    public InfluxDBOptions() {
    }

    public InfluxDBOptions(String serverURL, String token, String orgID, String bucket) {
        this.serverURL = serverURL;
        this.token = token;
        this.orgID = orgID;
        this.bucket = bucket;
    }

    @Override
    public String toString() {
        return "InfluxDBOptions [serverURL=" + serverURL + ", token=" + token + ", orgID=" + orgID + ", bucket="
                + bucket + "]";
    }


}
