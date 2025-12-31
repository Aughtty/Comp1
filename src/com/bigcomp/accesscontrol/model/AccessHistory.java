package com.bigcomp.accesscontrol.model;

import java.time.LocalDateTime;

/**
 * AccessHistory tracks zone transitions for precedence checks
 * Example: User must enter Building A before entering Lab inside Building A
 */
public class AccessHistory {
    private String badgeId;
    private String fromZoneId;
    private String toZoneId;
    private LocalDateTime accessTime;
    private String resourceId;

    public AccessHistory(String badgeId, String fromZoneId, String toZoneId, String resourceId, LocalDateTime accessTime) {
        this.badgeId = badgeId;
        this.fromZoneId = fromZoneId;
        this.toZoneId = toZoneId;
        this.accessTime = accessTime;
        this.resourceId = resourceId;
    }

    public String getBadgeId() { return badgeId; }
    public void setBadgeId(String badgeId) { this.badgeId = badgeId; }

    public String getFromZoneId() { return fromZoneId; }
    public void setFromZoneId(String fromZoneId) { this.fromZoneId = fromZoneId; }

    public String getToZoneId() { return toZoneId; }
    public void setToZoneId(String toZoneId) { this.toZoneId = toZoneId; }

    public LocalDateTime getAccessTime() { return accessTime; }
    public void setAccessTime(LocalDateTime accessTime) { this.accessTime = accessTime; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
}
