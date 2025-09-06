package com.example.api.drools;

/**
 * 构建结果类
 */
public class BuildResult {
    public enum Status {
        SUCCESS, FAILURE
    }
    
    private final Status status;
    private final String message;
    
    public BuildResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public String getMessage() {
        return message;
    }
}
