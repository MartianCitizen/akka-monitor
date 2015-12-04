package com.martiancitizen.akka.monitor;

import com.fasterxml.jackson.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterData {

    // Properties returned in JSON response
    String responder;
    String clusterLeader;
    Map<String, String> roleLeaders;
    List<List<String>> members;
    List<List<String>> unreachable;

    public class Node {
        public String service;
        private String ip;

        public String getIp() {
            return ip;
        }

        public String status;
        public String leader;
        private boolean foundByELB = false;

        public boolean isFoundByELB() {
            return foundByELB;
        }

        public boolean isNotFoundByELB() {
            return !foundByELB;
        }

        public void setFoundByELB(boolean flag) {
            foundByELB = flag;
        }

        public Node(String service, String ip, String status, String leader) {
            this.service = service;
            this.ip = ip;
            this.status = status;
            this.leader = leader;
            foundByELB = false;
        }
    }

    private List<Node> nodes = new ArrayList<>();

    public List<Node> getNodes() {
        return nodes;
    }

    public void setMembers(List<List<String>> members) {
        this.members = members;
        mapNodes(this.members);
    }

    public String getResponder() {
        return responder;
    }

    public String getClusterLeader() {
        return clusterLeader;
    }

    public Map<String, String> getRoleLeaders() {
        return roleLeaders;
    }

    public List<List<String>> getMembers() {
        return members;
    }

    public List<List<String>> getUnreachable() {
        return unreachable;
    }

    public void mapNodes(List<List<String>> memberList) {

        List<String> roleLeaderIps = this.roleLeaders.entrySet().stream()
                .map(m -> m.getValue().replaceFirst(".*@", "").replaceFirst("[:].*", ""))
                .collect(Collectors.toList());

        this.nodes = memberList.stream()
                .map(member -> {
                    String service = member.get(1).replace("[", "").replace("]", "");
                    String ip = member.get(0).replaceFirst(".*@", "").replaceFirst("[:].*", "");
                    String status = member.get(0).replaceFirst(".*status = ", "").replaceFirst("[)].*", "");
                    List<String> leadership = new ArrayList<>();
                    if (this.clusterLeader.contains(ip)) leadership.add("Cluster");
                    if (roleLeaderIps.contains(ip)) leadership.add("Role");
                    return new Node(service, ip, status, leadership.stream().collect(Collectors.joining(", ")));
                })
                .collect(Collectors.toList());

    }
}
