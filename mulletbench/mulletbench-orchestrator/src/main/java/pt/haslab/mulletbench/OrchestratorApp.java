package pt.haslab.mulletbench;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.haslab.mulletbench.utils.OrchestratorOptions;

public final class OrchestratorApp {

    private static final Logger logger = LogManager.getLogger();
    private OrchestratorApp() {
    }

    public static void main(String[] args) {
        OrchestratorOptions options;
        try{
            logger.info("Loading configuration file");
            options = OrchestratorOptions.load("config.yml");
        } catch (IOException e){
            logger.error("Failed to load configuration file" +  e);
            return;
        }
        logger.info(options.clients.toString());
        try{
            Orchestrator orchestrator = new Orchestrator(options);

            orchestrator.run();
        } catch(IOException e){
            logger.error("Failed to open ServerSocket. Aborted");
            e.printStackTrace();
        }

    }
}
