package com.bigcomp.accesscontrol.arp;

import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.model.Badge;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * BadgeUpdateProcessor handles badge update logic:
 * - Check if a badge requires update
 * - Handle badge held for update (vs swiped)
 * - Enforce grace periods
 * - Disable badges if grace period expires
 */
public class BadgeUpdateProcessor {
    private DB db;
    private static final long GRACE_PERIOD_DAYS = 7;  // 7 days to update after requirement

    public BadgeUpdateProcessor(DB db) {
        this.db = db;
    }

    /**
     * Check badge update status when swiped normally
     * Returns error message if update is required or expired, or null if OK
     */
    public String checkBadgeUpdateOnSwipe(Badge badge) {
        if (badge == null) return null;

        // Check if grace period has expired (badge should be disabled)
        if (badge.isUpdateGraceExpired()) {
            disableBadge(badge);
            return "Badge not valid (update grace period expired)";
        }

        // Check if update is required and in grace period
        if (badge.isRequiresUpdate()) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime graceEnd = badge.getUpdateGracePeriodEnd();
            
            if (graceEnd != null && now.isBefore(graceEnd)) {
                // Still within grace period
                long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(now, graceEnd);
                return "Badge must be updated (hold card to update. " + daysRemaining + " days remaining)";
            }
        }

        return null;  // Badge is OK
    }

    /**
     * Process a badge held for update (not swiped, just presented to reader)
     * Returns success message if update was successful, error message otherwise
     */
    public String processBadgeUpdate(Badge badge) {
        if (badge == null) {
            return "Badge not found";
        }

        // Only update badges that require it
        if (!badge.isRequiresUpdate()) {
            return "Badge does not require update";
        }

        // Check if grace period has expired
        if (badge.isUpdateGraceExpired()) {
            // Grace period expired, disable the badge
            disableBadge(badge);
            return "Badge cannot be updated (grace period expired, badge has been disabled)";
        }

        // Perform the update
        try {
            LocalDateTime now = LocalDateTime.now();
            badge.setLastUpdateTime(now);
            badge.setRequiresUpdate(false);
            badge.setUpdateDueDate(null);
            badge.setUpdateGracePeriodEnd(null);
            
            // Persist to database
            updateBadgeInDatabase(badge);
            
            return "Badge successfully updated";
        } catch (Exception e) {
            return "Error updating badge: " + e.getMessage();
        }
    }

    /**
     * Mark a badge as requiring update (called by system when rotation is needed)
     */
    public void markBadgeForUpdate(Badge badge) {
        if (badge == null) return;
        
        LocalDateTime now = LocalDateTime.now();
        badge.setRequiresUpdate(true);
        badge.setUpdateDueDate(now);
        badge.setUpdateGracePeriodEnd(now.plusDays(GRACE_PERIOD_DAYS));
        
        try {
            updateBadgeInDatabase(badge);
        } catch (Exception e) {
            System.err.println("Error marking badge for update: " + e.getMessage());
        }
    }

    /**
     * Disable a badge (called when grace period expires or as admin action)
     */
    private void disableBadge(Badge badge) {
        if (badge == null) return;
        badge.setActive(false);
        badge.setRequiresUpdate(false);
        try {
            updateBadgeInDatabase(badge);
        } catch (Exception e) {
            System.err.println("Error disabling badge: " + e.getMessage());
        }
    }

    /**
     * Update badge information in the database
     */
    private void updateBadgeInDatabase(Badge badge) throws Exception {
        // This is a placeholder - actual implementation depends on DB structure
        // For now, we can use the existing revokeBadge or add a new method
        // In a real system, this would update the requires_update, update_due_date, 
        // update_grace_period_end, and last_update_time fields in the Badges table
        
        if (!badge.isActive()) {
            db.revokeBadge(badge.getBadgeId());
        }
    }
}
