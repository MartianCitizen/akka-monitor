package com.martiancitizen.akka.monitor;

import org.apache.commons.cli.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

@SpringBootApplication
public class WebApplication {

    public static int numExpectedMembers;
    public static String environment = "foo";
    public final static Logger logger = LoggerFactory.getLogger(WebApplication.class);
    public static ConfigurableEnvironment appEnv;

    public static void main(String args[]) {

        ConfigurableApplicationContext context = SpringApplication.run(WebApplication.class, args);
        appEnv = context.getEnvironment();

        Options options = new Options()
                .addOption(OptionBuilder.withArgName("env").hasArg().withDescription("default environment to test").isRequired().create("env"))
                .addOption(OptionBuilder.withArgName("nem").hasArg().withDescription("expected number of members").isRequired().create("nti"));

        CommandLineParser parser = new BasicParser();
        Optional<CommandLine> line = Optional.empty();
        try {
            line = Optional.of(parser.parse(options, args));
            if (!line.isPresent()) throw new Exception("Parser returned null command line");
            switch (environment = line.get().getOptionValue("env").toLowerCase()) {
                case "prod": case "dev": // valid values
                    break;
                default:
                    throw new Exception("Invalid -env value");
            }
            numExpectedMembers = Integer.parseInt(line.get().getOptionValue("nem"));
        } catch (Exception exp) {
            System.err.println("Parsing failed.  Reason: " + exp.toString());
            System.exit(1);
        }

        new MonitorThread().scheduleStatusCheck();
    }

    public static void log(boolean isError, String msg) {
        if (isError)
            logger.error(msg);
        else
            logger.info(msg);
    }

    public static String getEnvironment() {
        return environment;
    }
}
