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
        public String ip;
        public String status;
        public String leader;
        public boolean foundByELB;
        Node (String service, String ip, String status, String leader) {
            this.service = service;
            this.ip = ip;
            this.status = status;
            this.leader = leader;
            foundByELB = false;
        }
    }
    public List<Node> nodes = new ArrayList<>();

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

    public List<String> getServiceNodeList( List<List<String>> memberList, String serviceType) {
        List<String> nodes = new ArrayList<>();
        memberList
                .stream()
                .filter(m -> m.get(1).contains(serviceType))
                .map(m -> m.get(0).replaceFirst(".*@", "").replaceFirst(", status = ", " (").toUpperCase())
                .forEach(nodes::add);
        return nodes;
    }

    public void mapNodes (List<List<String>> memberList) {
        List<String> roleLeaderIps = new ArrayList<>();
        roleLeaders
                .entrySet()
                .stream()
                .map(m -> m.getValue().replaceFirst(".*@", "").replaceFirst("[:].*",""))
                .forEach(roleLeaderIps::add);
        for (List<String> member : memberList) {
            String service = member.get(1).replace("[","").replace("]","");
            String ip = member.get(0).replaceFirst(".*@", "").replaceFirst("[:].*","");
            String status = member.get(0).replaceFirst(".*status = ","").replaceFirst("[)].*","");
            List<String> leadership = new ArrayList<>();
            if (clusterLeader.contains(ip)) leadership.add("Cluster");
            if (roleLeaderIps.contains(ip)) leadership.add("Role");
            nodes.add(new Node(service, ip, status, leadership.stream().collect(Collectors.joining(", "))));
        }
    }
}
