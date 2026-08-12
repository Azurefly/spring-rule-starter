package com.example.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<String> handleIllegalArgument(IllegalArgumentException exception) {
        return Result.fail(safeMessage(exception));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<String> handleIllegalState(IllegalStateException exception) {
        return Result.fail(safeMessage(exception));
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception exception) {
        return Result.fail(safeMessage(exception));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "internal_error" : message;
    }
}
