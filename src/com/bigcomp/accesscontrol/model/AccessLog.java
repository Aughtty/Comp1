package com.bigcomp.accesscontrol.model;

import java.time.LocalDateTime;

public class AccessLog {
    private LocalDateTime timestamp;
    private String badgeId;
    private String readerId;
    private String resourceId;
    private String userId;
    private String result; // GRANTED / DENIED
    private String message;
    private String denialReason; // Specific reason if DENIED (e.g., "EXPIRED", "NO_PERMISSION", "USAGE_LIMIT_EXCEEDED", "PRECEDENCE_VIOLATION")
    private String fromZoneId; // Zone the user is trying to exit from
    private String toZoneId;   // Zone the user is trying to enter

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getBadgeId() { return badgeId; }
    public void setBadgeId(String badgeId) { this.badgeId = badgeId; }
    public String getReaderId() { return readerId; }
    public void setReaderId(String readerId) { this.readerId = readerId; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDenialReason() { return denialReason; }
    public void setDenialReason(String denialReason) { this.denialReason = denialReason; }
    public String getFromZoneId() { return fromZoneId; }
    public void setFromZoneId(String fromZoneId) { this.fromZoneId = fromZoneId; }
    public String getToZoneId() { return toZoneId; }
    public void setToZoneId(String toZoneId) { this.toZoneId = toZoneId; }
}