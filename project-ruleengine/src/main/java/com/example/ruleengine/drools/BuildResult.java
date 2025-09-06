
package com.example.ruleengine.drools;

public class BuildResult {
    public enum Status { SUCCESS, FAILURE }

    private Status status;
    private String message;

    public BuildResult() {}
    public BuildResult(Status status, String message) { this.status = status; this.message = message; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
