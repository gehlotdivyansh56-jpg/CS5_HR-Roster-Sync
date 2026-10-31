package com.movesync.rostersync.exception;

import java.time.Instant;
import java.util.List;

public class ErrorResponse {

    private String code;
    private String message;
    private List<FieldErrorDetail> errors;
    private Instant timestamp = Instant.now();

    public ErrorResponse() {}

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String code, String message, List<FieldErrorDetail> errors) {
        this.code = code;
        this.message = message;
        this.errors = errors;
        this.timestamp = Instant.now();
    }

    public static class FieldErrorDetail {
        private String field;
        private String reason;
        private String sanitizedValue;

        public FieldErrorDetail() {}

        public FieldErrorDetail(String field, String reason, String sanitizedValue) {
            this.field = field;
            this.reason = reason;
            this.sanitizedValue = sanitizedValue;
        }

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getSanitizedValue() { return sanitizedValue; }
        public void setSanitizedValue(String sanitizedValue) { this.sanitizedValue = sanitizedValue; }
    }

    // Getters and Setters
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<FieldErrorDetail> getErrors() { return errors; }
    public void setErrors(List<FieldErrorDetail> errors) { this.errors = errors; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}

