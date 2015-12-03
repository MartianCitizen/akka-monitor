package com.martiancitizen.akka.monitor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Response {

    private String code;
    private String message;
    private ClusterData data;

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public ClusterData getData() {
        return data;
    }

}
