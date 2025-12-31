package com.bigcomp.accesscontrol.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Badge {
    private String badgeId;
    private String userId;
    private LocalDate expirationDate;
    private boolean active;
    private String currentZoneId;
    
    // Badge update fields
    private LocalDateTime updateDueDate;  // When the badge must be updated by
    private LocalDateTime updateGracePeriodEnd;  // Grace period for update (e.g., 7 days)
    private boolean requiresUpdate;  // Flag indicating this badge needs to be updated
    private LocalDateTime lastUpdateTime;  // When was the badge last updated

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
    
    public LocalDateTime getUpdateDueDate() { return updateDueDate; }
    public void setUpdateDueDate(LocalDateTime updateDueDate) { this.updateDueDate = updateDueDate; }
    
    public LocalDateTime getUpdateGracePeriodEnd() { return updateGracePeriodEnd; }
    public void setUpdateGracePeriodEnd(LocalDateTime updateGracePeriodEnd) { this.updateGracePeriodEnd = updateGracePeriodEnd; }
    
    public boolean isRequiresUpdate() { return requiresUpdate; }
    public void setRequiresUpdate(boolean requiresUpdate) { this.requiresUpdate = requiresUpdate; }
    
    public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    
    /**
     * Check if the badge update grace period has expired
     */
    public boolean isUpdateGraceExpired() {
        if (updateGracePeriodEnd == null) return false;
        return LocalDateTime.now().isAfter(updateGracePeriodEnd);
    }
}