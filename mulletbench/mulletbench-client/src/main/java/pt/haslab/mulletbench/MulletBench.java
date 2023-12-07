package pt.haslab.mulletbench;

import java.io.IOException;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.haslab.mulletbench.utils.ClientOptions;

public final class MulletBench {

    private static final Logger logger = LogManager.getLogger();

    private MulletBench() {
    }

    private static ClientOptions loadOptions(String[] args) throws IOException{
        if(args.length > 1 && args[0].equals("-c")){
            logger.info("Loading config file " + args[1]);
            return ClientOptions.load(args[1]);
        } else {
            logger.info("Loading default config file");
            return ClientOptions.load();
        }
    }

    public static void main(String[] args) {

        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");

        System.out.println(Arrays.toString(args));

        ClientOptions options;
        try {
            options = loadOptions(args);
        } catch (IOException e) {
            logger.error("Failed to load configuration file", e);
            return;
        }

        // logger.info(options.toString());

        try {
            BenchmarkClient client = new BenchmarkClient(options);

            client.run();
        } catch (Exception e) {
            logger.error("Connection to orchestrator failed: " + options.orchestratorAddress);
            logger.error(e);
        }
    }
}
