package com.martiancitizen.akka.monitor;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.ui.Model;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.martiancitizen.akka.monitor.ClusterData.Node;

public class Status {

    private String env;
    private String uriAwsELB;
    private Optional<Model> modelOpt = Optional.empty();

    public Status(String env, Optional<Model> modelOpt) {
        this.env = env;
        this.modelOpt = modelOpt;
        this.uriAwsELB = WebApplication.appEnv.getProperty(env.toLowerCase() + ".uri");
    }

    public Status withClusterInfo() {

        addToModel("env", env);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a");
        addToModel("asoftime", java.time.LocalDateTime.now().format(formatter));

        SimpleClientHttpRequestFactory client = new SimpleClientHttpRequestFactory();
        client.setConnectTimeout(10000);

        /*
        Get cluster status
         */

        RestTemplate restTemplate = new RestTemplate(client);
        Optional<Response> respOpt = Optional.empty();
        try {
            respOpt = Optional.of(restTemplate.getForObject(this.uriAwsELB + "/admin/clusterstate", Response.class));
        } catch (Exception e) {
            addToModel("systemError", "ERROR: " + e.toString()); // forwarding failed
            WebApplication.log(true, e.toString());
            return this;
        }
        Response resp = respOpt.get();

        if (!resp.getCode().equals("200")) {
            String msg = "Unexpected HTTP response code " + resp.getCode();
            addToModel("systemError", "ERROR: " + msg);
            WebApplication.log(true, msg);
            return this;
        }

        ClusterData cluster = resp.getData();

        addToModel("responder", cluster.getResponder());

        if (cluster.getUnreachable().size() < 1) {
            addToModel("unreachable", "NONE");
        } else {
            addToModel("unreachable", cluster.getUnreachable().toString());
            WebApplication.log(true, "Unreachable nodes: " + cluster.getUnreachable().toString());
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

        addToModel("nodes", cluster.getNodes());

        /*
        Now verify that ELBs forward to all nodes
         */

        addToModel("UriAwsELB", this.uriAwsELB);
        addToModel("UriAwsELB_status", checkClusterELB(cluster, restTemplate, WebApplication.numExpectedMembers));

        return this;
    }

    private void addToModel(String attr, Object value) {
        if (this.modelOpt.isPresent()) this.modelOpt.get().addAttribute(attr, value);
    }

    private String checkClusterELB(ClusterData cluster, RestTemplate restTemplate, int numNodes) {

        String msg = "";
        String url = this.uriAwsELB + "/admin/address";

        List<Node> nodes = cluster.getNodes();
        List<String> errors = new ArrayList<>();
        IntStream.range(0, numNodes * 2).forEach(i -> {
            String ip = restTemplate.getForObject(url, String.class).replaceFirst(".*\\x5b", "").replaceFirst("\\x5d.*", "");
            Optional<Node> nodeOpt = nodes.stream()
                    .filter(node -> node.getIp().equals(ip))
                    .findFirst();
            if (nodeOpt.isPresent()) {
                nodeOpt.get().setFoundByELB(true);
            } else {
                String err = "ELB forwarded to non-service node " + ip;
                errors.add(err);
                WebApplication.log(true, "ELB error: " + this.uriAwsELB + " " + err);
            }
        });

        List<String> nodesFound = nodes.stream()
                .filter(Node::isFoundByELB).map(Node::getIp)
                .collect(Collectors.toList());

        List<String> nodesNotFound = nodes.stream()
                .filter(Node::isNotFoundByELB)
                .map(Node::getIp)
                .map(ip -> {
                    WebApplication.log(true, "ELB error: node not found: " + ip);
                    return ip;
                })
                .collect(Collectors.toList());

        msg += nodesFound.isEmpty() ? "HIT: NONE " : nodesFound.stream().collect(Collectors.joining(", ", "HIT: ", " "));
        msg += nodesNotFound.isEmpty() ? "MISS: NONE " : nodesNotFound.stream().collect(Collectors.joining(", ", "MISS: ", " "));
        msg += errors.isEmpty() ? "" : errors.stream().collect(Collectors.joining(", "));

        return msg;
    }

}
