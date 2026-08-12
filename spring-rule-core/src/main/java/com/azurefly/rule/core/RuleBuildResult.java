package com.azurefly.rule.core;

/**
 * Result returned by rule validation or installation.
 */
public final class RuleBuildResult {
    public enum Status {
        SUCCESS,
        FAILURE
    }

    private final Status status;
    private final String message;

    public RuleBuildResult(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public static RuleBuildResult success(String message) {
        return new RuleBuildResult(Status.SUCCESS, message);
    }

    public static RuleBuildResult failure(String message) {
        return new RuleBuildResult(Status.FAILURE, message);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
