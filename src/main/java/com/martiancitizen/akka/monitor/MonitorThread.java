package com.martiancitizen.akka.monitor;

import org.springframework.ui.Model;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.*;

/**
 * Created by johnchamberlain on 3/3/15.
 */
public class MonitorThread {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    public void scheduleStatusCheck() {
        WebApplication.log(false, "Monitor thread starting");
        final Runnable requestStatus = () -> {
            WebApplication.log(false, "Getting status");
            new Status(WebApplication.environment, Optional.<Model>empty()).withClusterInfo();
        };
       scheduler.scheduleAtFixedRate(requestStatus, 1, 1, MINUTES);
    }

}
