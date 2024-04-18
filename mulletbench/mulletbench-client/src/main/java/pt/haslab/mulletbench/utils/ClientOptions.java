package pt.haslab.mulletbench.utils;

import java.io.Reader;

import pt.haslab.mulletbench.ResourceAccess;
import pt.haslab.mulletbench.WorkloadType;

import java.io.IOException;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

public class ClientOptions {

    public String clientId;
    public String orchestratorAddress = "127.0.0.1";

    public int orchestratorPort = 27205;

    public String dataset = "Accel";
    public String dataFile = "data_1601_accel_watch.txt";

    public String target = "influx";

    public WorkloadType type = WorkloadType.INSERT;

    public boolean currentTime = true;
    public boolean sharedDataset = true;
    public boolean sharedConnection = true;
    public int numWorkers = 1;

    public InfluxDBOptions influx = new InfluxDBOptions();

    public IoTDBOptions iotdb = new IoTDBOptions();

    public TimescaleDBOptions timescale = new TimescaleDBOptions();

    public QueryOptions query = new QueryOptions();
    public InsertionOptions insertion = new InsertionOptions();

    public ClientOptions() {
    }

    private static ClientOptions loadWithReader(Reader configFileReader){
        LoaderOptions lo = new LoaderOptions(); //TODO check default LoaderOptions

        return new Yaml(new Constructor(ClientOptions.class,lo)).load(configFileReader);
    }

    public static ClientOptions load() throws IOException{
        Reader configFileReader = ResourceAccess.getResourceBufferedReader("config.yml");

        return loadWithReader(configFileReader);
    }

    public static ClientOptions load(String filePath) throws IOException {
        Reader configFileReader = ResourceAccess.getFileBufferedReader("/config/" + filePath, ClientOptions.class);

        return loadWithReader(configFileReader);
    }

    @Override
    public String toString() {
        return "Options [dataFile=" + dataFile + ", targetDB=" + target + ", influx=" + influx.toString() + ", iotdb=" + iotdb.toString()
                + "]";
    }
}
