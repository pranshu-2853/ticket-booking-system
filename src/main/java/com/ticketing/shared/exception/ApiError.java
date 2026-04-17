package com.ticketing.shared.exception;

import java.time.LocalDateTime;

public class ApiError {

    private String message;
    private int status;
    private LocalDateTime timestamp;
    private String path;

    public ApiError(String message, int status, String path) {
        this.message = message;
        this.status = status;
        this.timestamp = LocalDateTime.now();
        this.path = path;
    }

    public String getMessage() { return message; }
    public int getStatus() { return status; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getPath() { return path; }
}