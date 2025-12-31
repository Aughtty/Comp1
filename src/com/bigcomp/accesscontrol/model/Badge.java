package com.bigcomp.accesscontrol.model;

import java.time.LocalDate;

public class Badge {
    private String badgeId;
    private String userId;
    private LocalDate expirationDate;
    private boolean active;
    private String currentZoneId;

    // Badge update workflow
    private boolean requiresUpdate;
    private java.time.LocalDateTime updateDueDate;
    private java.time.LocalDateTime updateGracePeriodEnd;
    private java.time.LocalDateTime lastUpdateTime;

    public String getBadgeId() { return badgeId; }
    public void setBadgeId(String badgeId) { this.badgeId = badgeId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCurrentZoneId() { return currentZoneId; }
    public void setCurrentZoneId(String currentZoneId) { this.currentZoneId = currentZoneId; }

    public boolean isRequiresUpdate() { return requiresUpdate; }
    public void setRequiresUpdate(boolean requiresUpdate) { this.requiresUpdate = requiresUpdate; }
    public java.time.LocalDateTime getUpdateDueDate() { return updateDueDate; }
    public void setUpdateDueDate(java.time.LocalDateTime updateDueDate) { this.updateDueDate = updateDueDate; }
    public java.time.LocalDateTime getUpdateGracePeriodEnd() { return updateGracePeriodEnd; }
    public void setUpdateGracePeriodEnd(java.time.LocalDateTime updateGracePeriodEnd) { this.updateGracePeriodEnd = updateGracePeriodEnd; }
    public java.time.LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(java.time.LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
}