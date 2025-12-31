package com.bigcomp.accesscontrol.arp;

import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.log.CSVLogger;
import com.bigcomp.accesscontrol.model.*;
import com.bigcomp.accesscontrol.util.ProfileManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class AccessProcessor {
    private DB db;
    private CSVLogger csvLogger;
    private ProfileManager profileManager;
    private BadgeUpdateProcessor badgeUpdateProcessor;
    private List<AccessEventListener> listeners = new ArrayList<>();

    public AccessProcessor(DB db, CSVLogger csvLogger) { 
        this.db = db;
        this.csvLogger = csvLogger;
        this.profileManager = db.getProfileManager();
        this.badgeUpdateProcessor = new BadgeUpdateProcessor(db);
    }

    public void addListener(AccessEventListener l) { listeners.add(l); }

    public AccessLog processSwipe(String badgeId, String readerId) {
        AccessLog log = new AccessLog();
        log.setTimestamp(LocalDateTime.now());
        log.setBadgeId(badgeId);
        log.setReaderId(readerId);

        Optional<Badge> ob = db.findBadge(badgeId);
        Optional<Reader> or = db.findReader(readerId);

        if (!ob.isPresent()) {
            log.setResult("DENIED");
            log.setMessage("Badge not found");
            log.setDenialReason("NOT_FOUND");
            logAndNotify(log, null);
            return log;
        }
        if (!or.isPresent()) {
            log.setResult("DENIED");
            log.setMessage("Reader not found");
            log.setDenialReason("READER_NOT_FOUND");
            logAndNotify(log, null);
            return log;
        }

        Badge b = ob.get();
        Reader r = or.get();
        Optional<Resource> osp = db.findResourceById(r.getResourceId());
        if (!osp.isPresent()) {
            log.setResult("DENIED");
            log.setMessage("Resource missing");
            log.setDenialReason("RESOURCE_NOT_FOUND");
            logAndNotify(log, b);
            return log;
        }
        Resource res = osp.get();
        log.setResourceId(res.getResourceId());
        log.setFromZoneId(res.getFromZoneId());
        log.setToZoneId(res.getToZoneId());

        // ===== Check 0: Is Badge in Update Mode? =====
        if (r.getUpdateMode() == 1) {
            // Badge is being held for update, not swiped
            return processBadgeUpdate(badgeId, readerId);
        }

        // ===== Check 0a: Is Resource Uncontrolled? =====
        if (!res.isControlled()) {
            // Uncontrolled resources grant access immediately
            log.setResult("GRANTED");
            log.setMessage("Access granted (uncontrolled resource)");
            
            // Update badge's current zone if moving through resource
            String fromZone = res.getFromZoneId();
            String toZone = res.getToZoneId();
            if (fromZone != null && toZone != null && !toZone.equals(b.getCurrentZoneId())) {
                db.updateBadgeCurrentZone(b.getBadgeId(), toZone);
                b.setCurrentZoneId(toZone);
            }
            
            logAndNotify(log, b);
            return log;
        }

        // ===== Check 1: Badge Basic Validity =====
        if (!b.isActive()) {
            log.setResult("DENIED");
            log.setMessage("Badge inactive");
            log.setDenialReason("INACTIVE");
            logAndNotify(log, b);
            return log;
        }

        // ===== Check 2: Badge Update Status =====
        String updateMessage = badgeUpdateProcessor.checkBadgeUpdateOnSwipe(b);
        if (updateMessage != null) {
            log.setResult("DENIED");
            log.setMessage(updateMessage);
            log.setDenialReason("UPDATE_REQUIRED");
            logAndNotify(log, b);
            return log;
        }

        // ===== Check 3: Badge Expiration =====
        if (b.getExpirationDate() != null && b.getExpirationDate().isBefore(LocalDate.now())) {
            log.setResult("DENIED");
            log.setMessage("Badge expired");
            log.setDenialReason("EXPIRED");
            logAndNotify(log, b);
            return log;
        }

        // ===== Check 4: Resource Group Verification =====
        Optional<String> og = db.findGroupForResource(res.getResourceId());
        if (!og.isPresent()) {
            log.setResult("DENIED");
            log.setMessage("Resource not in any group");
            log.setDenialReason("NO_GROUP");
            logAndNotify(log, b);
            return log;
        }
        String group = og.get();

        // ===== Check 5: Zone Transition Validity =====
        String badgeZone = b.getCurrentZoneId();
        if (badgeZone == null) badgeZone = "Z_OUTSIDE";
        String fromZone = res.getFromZoneId();
        String toZone = res.getToZoneId();
        
        if (fromZone != null && !fromZone.equals(badgeZone)) {
            log.setResult("DENIED");
            log.setMessage("Badge not in entry zone. Current: " + badgeZone + ", Required: " + fromZone);
            log.setDenialReason("WRONG_ZONE");
            logAndNotify(log, b);
            return log;
        }

        // ===== Check 6: Precedence Check =====
        String precedenceError = checkPrecedence(b, res, group);
        if (precedenceError != null) {
            log.setResult("DENIED");
            log.setMessage(precedenceError);
            log.setDenialReason("PRECEDENCE_VIOLATION");
            logAndNotify(log, b);
            return log;
        }

        // ===== Check 7: Profile-Based Authorization with Time Filters =====
        LocalDateTime accessTime = LocalDateTime.now();
        List<String> badgeProfileNames = db.getProfilesForBadge(b.getBadgeId());
        boolean allowed = false;
        String denialReason = "No profile grants access to group " + group;
        
        for (String profileName : badgeProfileNames) {
            Profile profile = profileManager.getProfile(profileName);
            if (profile != null && profile.grantsAccess(group, accessTime)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            log.setResult("DENIED");
            log.setMessage(denialReason);
            log.setDenialReason("NO_PERMISSION");
            logAndNotify(log, b);
            return log;
        }

        // ===== Check 8: Usage Count Limits =====
        int dailyLimit = getResourceGroupDailyLimit(group);
        if (dailyLimit > 0) {
            int used = db.getAccessCountInWindow(b.getBadgeId(), group, "DAILY");
            if (used >= dailyLimit) {
                log.setResult("DENIED");
                log.setMessage("Daily usage limit reached (" + used + "/" + dailyLimit + ")");
                log.setDenialReason("USAGE_LIMIT_EXCEEDED");
                logAndNotify(log, b);
                return log;
            }
        }

        // ===== All Checks Passed: Grant Access =====
        log.setResult("GRANTED");
        log.setMessage("Access granted");
        
        // Increment usage counter
        if (dailyLimit > 0) {
            db.incrementUsageCount(b.getBadgeId(), group);
        }
        
        // Update badge's current zone if moving through resource
        if (fromZone != null && toZone != null && !toZone.equals(badgeZone)) {
            db.updateBadgeCurrentZone(b.getBadgeId(), toZone);
            b.setCurrentZoneId(toZone);
        }
        
        // Record access history for precedence checks
        db.recordAccessHistory(b.getBadgeId(), fromZone, toZone, res.getResourceId(), "GRANTED");

        logAndNotify(log, b);
        return log;
    }

    /**
     * Check precedence rules
     * Example: Must enter Building before accessing Lab inside Building
     */
    private String checkPrecedence(Badge badge, Resource resource, String group) {
        // For now, a simple check: if accessing a secure zone, must have recently accessed building
        String toZone = resource.getToZoneId();
        
        // High-security zones may require prior zone access
        if (toZone != null && toZone.contains("LAB")) {
            Optional<AccessHistory> lastAccess = db.getMostRecentAccess(badge.getBadgeId());
            if (!lastAccess.isPresent()) {
                return "Must enter building before accessing lab";
            }
            
            AccessHistory ah = lastAccess.get();
            LocalDateTime now = LocalDateTime.now();
            long minutesSinceLastAccess = ChronoUnit.MINUTES.between(ah.getAccessTime(), now);
            
            // Allow if accessed building within last 2 hours
            if (minutesSinceLastAccess > 120) {
                return "Must re-enter building to access lab";
            }
        }
        
        return null; // No precedence violation
    }

    /**
     * Get daily usage limit for a resource group
     */
    private int getResourceGroupDailyLimit(String groupName) {
        // Hardcoded limits - in production, store in database
        Map<String, Integer> limits = new HashMap<>();
        limits.put("G_FREE_DRINKS", 5);
        limits.put("G_PUBLIC_ACCESS", Integer.MAX_VALUE);
        limits.put("G_TOP_SECRET", Integer.MAX_VALUE);
        return limits.getOrDefault(groupName, 0);
    }

    /**
     * Process badge update (badge held to reader)
     */
    public AccessLog processBadgeUpdate(String badgeId, String readerId) {
        AccessLog log = new AccessLog();
        log.setTimestamp(LocalDateTime.now());
        log.setBadgeId(badgeId);
        log.setReaderId(readerId);

        Optional<Badge> ob = db.findBadge(badgeId);
        if (!ob.isPresent()) {
            log.setResult("DENIED");
            log.setMessage("Badge not found");
            logAndNotify(log, null);
            return log;
        }

        Badge b = ob.get();
        String updateMessage = badgeUpdateProcessor.processBadgeUpdate(b);
        
        if (updateMessage.contains("successfully")) {
            log.setResult("GRANTED");
        } else {
            log.setResult("DENIED");
        }
        log.setMessage(updateMessage);
        
        logAndNotify(log, b);
        return log;
    }

    private void logAndNotify(AccessLog log, Badge badge) {
        db.insertAccessLog(log);
        
        // Also log to CSV with user info
        String userName = "Unknown";
        if (badge != null) {
            log.setUserId(badge.getUserId());
            Optional<User> ouser = db.findUserById(badge.getUserId());
            if (ouser.isPresent()) {
                userName = ouser.get().getFullName();
            }
        }
        csvLogger.logAccess(log, userName);
        
        notifyListeners(log);
    }

    private void notifyListeners(AccessLog log) {
        for (AccessEventListener l : listeners) l.onAccessEvent(log);
    }

    public interface AccessEventListener {
        void onAccessEvent(AccessLog log);
    }
}
