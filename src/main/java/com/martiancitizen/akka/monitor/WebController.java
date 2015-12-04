package com.martiancitizen.akka.monitor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class WebController {

    @RequestMapping(value = "/status", method = RequestMethod.GET)
    public String status(@RequestParam(value = "env", defaultValue = "dev") String env, Model model) {
        new Status(env, Optional.of(model)).withClusterInfo();
        return "status";
    }

    @RequestMapping(value = "/ping", method = RequestMethod.GET)
    public String ping(Model model) {
        return "ping";
    }
}
