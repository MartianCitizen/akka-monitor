package com.martiancitizen.akka.monitor;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.ui.Model;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Status {

    private Optional<Model> modelOpt = Optional.empty();
    private Optional<ClusterData> clusterDataOpt = Optional.empty();

    public Status(String env, Optional<Model> modelOpt) {

        this.modelOpt = modelOpt;

        addToModel("env", env);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a");
        addToModel("asoftime", java.time.LocalDateTime.now().format(formatter));

        String UriAwsELB = "NA";
        switch (env.toLowerCase()) {
            case "prod":
                UriAwsELB = WebApplication.appEnv.getProperty("prod.elb.uri");
                break;
            case "dev":
                UriAwsELB = WebApplication.appEnv.getProperty("dev.elb.uri");
                break;
            default:
                String msg = "Unknown environment specified: " + env;
                addToModel("systemError", "ERROR: " + msg);
                WebApplication.log(true, msg);
                return;
        }

        SimpleClientHttpRequestFactory client = new SimpleClientHttpRequestFactory();
        client.setConnectTimeout(10000);

        /*
        Get cluster status
         */

        RestTemplate restTemplate = new RestTemplate(client);
        Optional<Response> respOpt = Optional.empty();
        try {
            respOpt = Optional.of(restTemplate.getForObject(UriAwsELB + "/admin/clusterstate", Response.class));
        } catch (Exception e) {
            addToModel("systemError", "ERROR: " + e.toString()); // forwarding failed
            WebApplication.log(true, e.toString());
            return;
        }
        Response resp = respOpt.get();

        if (!resp.getCode().equals("200")) {
            String msg = "Unexpected HTTP response code " + resp.getCode();
            addToModel("systemError", "ERROR: " + msg);
            WebApplication.log(true, msg);
        } else {
            ClusterData cluster = resp.getData();
            clusterDataOpt = Optional.of(cluster);

            addToModel("responder", cluster.getResponder());

            if (cluster.unreachable.size() < 1) {
                addToModel("unreachable", "NONE");
            } else {
                addToModel("unreachable", cluster.unreachable.toString());
                WebApplication.log(true, "Unreachable nodes: " + cluster.unreachable.toString());
            }


            int numMembers = cluster.getMembers().size();
            addToModel("nem_actual", numMembers);
            addToModel("nem_expected", WebApplication.numExpectedMembers);
            if (numMembers == WebApplication.numExpectedMembers) {
                addToModel("nem_result", "YES");
            } else {
                addToModel("nem_result", "ERROR");
                WebApplication.log(true, "Number of cluster members expected: " + WebApplication.numExpectedMembers + " Actual: " + numMembers);
            }

            addToModel("nodes", cluster.nodes);
        }

        /*
        Now verify that ELBs forward to all nodes
         */

        addToModel("UriAwsELB", UriAwsELB);
        addToModel("UriAwsELB_status", checkClusterELB(restTemplate, UriAwsELB, WebApplication.numExpectedMembers));
    }

    private void addToModel(String attr, Object value) {
        if (modelOpt.isPresent()) modelOpt.get().addAttribute(attr, value);
    }

    private String checkClusterELB(RestTemplate restTemplate, String elbUrl, int numNodes) {
        String msg = "";
        if (!clusterDataOpt.isPresent()) {
            msg = "Cluster data not available";
            WebApplication.log(true, "ELB error: " + elbUrl + " " + msg);
            return msg;
        }
        ClusterData cluster = clusterDataOpt.get();
        String url = elbUrl + "/admin/address";
        List<ClusterData.Node> nodes = cluster.nodes;
        List<String> errors = new ArrayList<>();
        IntStream.range(0, numNodes * 2).forEach(i -> {
            String ip = restTemplate.getForObject(url, String.class).replaceFirst(".*\\x5b", "").replaceFirst("\\x5d.*", "");
            Optional<ClusterData.Node> nodeOpt = nodes.stream().filter(n -> n.ip.equals(ip)).findFirst();
            if (!nodeOpt.isPresent()) {
                nodeOpt.get().foundByELB = true;
            } else {
                String err = "ELB forwarded to non-service node " + ip;
                errors.add(err);
                WebApplication.log(true, "ELB error: " + elbUrl + " " + err);
            }
        });
        List<String> nodesFound = nodes.stream().filter(n -> n.foundByELB).map(n -> n.ip).collect(Collectors.toList());
        List<String> nodesNotFound = nodes.stream().filter(n -> !n.foundByELB).map(n -> n.ip).collect(Collectors.toList());
        msg += nodesFound.isEmpty() ? "HIT: NONE " : nodesFound.stream().collect(Collectors.joining(", ", "HIT: ", " "));
        msg += nodesNotFound.isEmpty() ? "MISS: NONE " : nodesNotFound.stream().collect(Collectors.joining(", ", "MISS: ", " "));
        msg += errors.stream().collect(Collectors.joining(", "));
        if (!nodesNotFound.isEmpty()) {
            WebApplication.log(true, "ELB error: " + elbUrl + " " + msg);
        }
        return msg;
    }

}
