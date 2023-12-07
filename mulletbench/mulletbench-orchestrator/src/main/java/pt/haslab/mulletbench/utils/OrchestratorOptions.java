package pt.haslab.mulletbench.utils;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

import pt.haslab.mulletbench.ResourceAccess;

public class OrchestratorOptions {

    public String database;
    public List<StageOptions> stages;
    public List<ClientOptions> clients;
    public List<NodeOptions> nodes;
    public String monitoringInterval;
    public String resultsFolder;
    public String clientContainerBase;

    public short finalMonitoringPeriodS = 0;

    private static OrchestratorOptions loadWithReader(Reader configFileReader){
        LoaderOptions lo = new LoaderOptions(); //TODO check default LoaderOptions

        return new Yaml(new Constructor(OrchestratorOptions.class,lo)).load(configFileReader);
    }

    public static OrchestratorOptions load() throws FileNotFoundException, IOException{
        Reader configFileReader = ResourceAccess.getResourceBufferedReader("config.yaml");

        return loadWithReader(configFileReader);
    }

    public static OrchestratorOptions load(String filePath) throws IOException {
        Reader configFileReader = ResourceAccess.getFileBufferedReader("/config/" + filePath, OrchestratorOptions.class);

        return loadWithReader(configFileReader);
    }

}
