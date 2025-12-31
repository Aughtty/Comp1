package com.bigcomp.accesscontrol.arp;

import com.bigcomp.accesscontrol.db.DB;
import com.bigcomp.accesscontrol.log.CSVLogger;
import com.bigcomp.accesscontrol.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AccessProcessor {
    private final DB db;
    private final CSVLogger csvLogger;
    private final UsageTracker usageTracker = new UsageTracker();
    private final Map<String, UsageTracker.Limits> limitConfig;
    private final Map<String, Deque<AccessHistory>> histories = new ConcurrentHashMap<>();
    private final int precedenceWindowMinutes = 30;
    private final List<AccessEventListener> listeners = new ArrayList<>();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public AccessProcessor(DB db, CSVLogger csvLogger) {
        this.db = db;
        this.csvLogger = csvLogger;
        this.limitConfig = loadUsageLimits();
    }

    public void addListener(AccessEventListener l) { listeners.add(l); }

    // Normal swipe (open resource)
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
            logAndNotify(log, null);
            return log;
        }
        if (!or.isPresent()) {
            log.setResult("DENIED");
            log.setMessage("Reader not found");
            logAndNotify(log, null);
            return log;
        }

        Badge b = ob.get();
        Reader r = or.get();
        Optional<Resource> osp = db.findResourceById(r.getResourceId());
        if (!osp.isPresent()) {
            log.setResult("DENIED");
            log.setMessage("Resource missing");
            logAndNotify(log, b);
            return log;
        }
        Resource res = osp.get();
        log.setResourceId(res.getResourceId());
        log.setFromZoneId(res.getFromZoneId());
        log.setToZoneId(res.getToZoneId());

        // If resource is uncontrolled, allow immediately
        if (!res.isControlled()) {
            log.setResult("GRANTED");
            log.setMessage("Resource currently uncontrolled");
            logAndNotify(log, b);
            return log;
        }

        // Basic badge checks
        if (!b.isActive()) {
            log.setResult("DENIED");
            log.setMessage("Badge inactive");
            logAndNotify(log, b);
            return log;
        }
        if (b.getExpirationDate() != null && b.getExpirationDate().isBefore(LocalDate.now())) {
            log.setResult("DENIED");
            log.setMessage("Badge expired");
            logAndNotify(log, b);
            return log;
        }

        LocalDateTime now = log.getTimestamp();
        if (b.isRequiresUpdate()) {
            if (b.getUpdateGracePeriodEnd() != null && now.isAfter(b.getUpdateGracePeriodEnd())) {
                log.setResult("DENIED");
                log.setMessage("Badge disabled: update grace expired");
                logAndNotify(log, b);
                return log;
            }
            if (b.getUpdateDueDate() != null && now.isAfter(b.getUpdateDueDate())) {
                log.setMessage("Badge must be updated (grace until " + fmt(b.getUpdateGracePeriodEnd()) + ")");
            }
        }

        Optional<String> og = db.findGroupForResource(res.getResourceId());
        if (!og.isPresent()) {
            log.setResult("DENIED");
            log.setMessage("Resource not in any group");
            logAndNotify(log, b);
            return log;
        }
        String group = og.get();
        List<String> bProfiles = db.getProfilesForBadge(b.getBadgeId());

        // Zone check
        String badgeZone = b.getCurrentZoneId() == null ? "Z_OUTSIDE" : b.getCurrentZoneId();
        String fromZone = res.getFromZoneId();
        String toZone = res.getToZoneId();
        if (fromZone != null || toZone != null) {
            boolean inAllowedZone = (fromZone != null && fromZone.equals(badgeZone)) ||
                    (toZone != null && toZone.equals(badgeZone));
            if (!inAllowedZone) {
                log.setResult("DENIED");
                log.setMessage("Badge not in allowed zone");
                logAndNotify(log, b);
                return log;
            }
        }

        if (!checkPrecedence(b, res, now)) {
            log.setResult("DENIED");
            log.setMessage("Precedence rule: enter parent zone first");
            logAndNotify(log, b);
            return log;
        }

        boolean allowed = hasProfileAccess(bProfiles, group, now);
        if (!allowed) {
            log.setResult("DENIED");
            log.setMessage("No profile/time window for group " + group);
            logAndNotify(log, b);
            return log;
        }

        // Usage limits
        UsageTracker.Limits limits = limitConfig.getOrDefault(group, new UsageTracker.Limits(0,0,0));
        if (limits.perDay > 0) {
            int usedToday = db.getUsageCountToday(b.getBadgeId(), group);
            if (usedToday >= limits.perDay) {
                log.setResult("DENIED");
                log.setMessage("Daily limit reached (" + usedToday + "/" + limits.perDay + ")");
                logAndNotify(log, b);
                return log;
            }
        }
        Optional<String> limitMsg = usageTracker.checkAndIncrement(b.getBadgeId(), group, limits, now);
        if (limitMsg.isPresent()) {
            log.setResult("DENIED");
            log.setMessage(limitMsg.get());
            logAndNotify(log, b);
            return log;
        }

        // Granted
        log.setResult("GRANTED");
        if (log.getMessage() == null) log.setMessage("Access granted");
        if (limits.perDay > 0) db.incrementUsageCount(b.getBadgeId(), group);

        if (fromZone != null && toZone != null && fromZone.equals(badgeZone) && !toZone.equals(badgeZone)) {
            db.updateBadgeCurrentZone(b.getBadgeId(), toZone);
            b.setCurrentZoneId(toZone);
            recordHistory(b.getBadgeId(), fromZone, toZone, res.getResourceId(), now);
        }

        logAndNotify(log, b);
        return log;
    }

    // Hold badge to update code
    public AccessLog processBadgeUpdate(String badgeId, String readerId) {
        AccessLog log = new AccessLog();
        log.setTimestamp(LocalDateTime.now());
        log.setBadgeId(badgeId);
        log.setReaderId(readerId);

        Optional<Badge> ob = db.findBadge(badgeId);
        Optional<Reader> or = db.findReader(readerId);
        if (!ob.isPresent()) { log.setResult("DENIED"); log.setMessage("Badge not found"); logAndNotify(log, null); return log; }
        if (!or.isPresent()) { log.setResult("DENIED"); log.setMessage("Reader not found"); logAndNotify(log, null); return log; }
        Badge b = ob.get();

        if (!b.isRequiresUpdate()) {
            log.setResult("GRANTED");
            log.setMessage("Badge does not require update");
            logAndNotify(log, b);
            return log;
        }

        LocalDateTime now = log.getTimestamp();
        if (b.getUpdateGracePeriodEnd() != null && now.isAfter(b.getUpdateGracePeriodEnd())) {
            log.setResult("DENIED");
            log.setMessage("Update window expired");
            logAndNotify(log, b);
            return log;
        }

        b.setRequiresUpdate(false);
        b.setLastUpdateTime(now);
        b.setUpdateDueDate(now.plusMonths(3));
        b.setUpdateGracePeriodEnd(now.plusMonths(3).plusDays(7));
        db.updateBadgeUpdateStatus(b);

        log.setResult("GRANTED");
        log.setMessage("Badge updated successfully");
        logAndNotify(log, b);
        return log;
    }

    private boolean hasProfileAccess(List<String> profileNames, String group, LocalDateTime now) {
        for (String p : profileNames) {
            if ("P_ADMIN".equalsIgnoreCase(p)) return true;
            Optional<Profile> op = db.findProfileByName(p);
            if (!op.isPresent()) continue;
            for (AccessRight r : op.get().getRights()) {
                if (group.equals(r.getGroupName()) && r.getTimeFilter().matches(now)) return true;
            }
        }
        return false;
    }

    private boolean checkPrecedence(Badge badge, Resource res, LocalDateTime now) {
        String from = res.getFromZoneId();
        if (from == null || "Z_OUTSIDE".equalsIgnoreCase(from)) return true;
        Deque<AccessHistory> deque = histories.getOrDefault(badge.getBadgeId(), new ArrayDeque<>());
        for (AccessHistory h : deque) {
            if (from.equals(h.getToZoneId()) && !h.getAccessTime().isBefore(now.minusMinutes(precedenceWindowMinutes))) {
                return true;
            }
        }
        return false;
    }

    private void recordHistory(String badgeId, String from, String to, String resId, LocalDateTime now) {
        histories.computeIfAbsent(badgeId, k -> new ArrayDeque<>());
        Deque<AccessHistory> deque = histories.get(badgeId);
        deque.addFirst(new AccessHistory(badgeId, from, to, resId, now));
        while (deque.size() > 50) deque.removeLast();
    }

    private Map<String, UsageTracker.Limits> loadUsageLimits() {
        Map<String, UsageTracker.Limits> map = new HashMap<>();
        java.io.File f = new java.io.File("data/usage_limits.properties");
        if (f.exists()) {
            Properties p = new Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) { p.load(fis); } catch (Exception ignored) { }
            for (String name : p.stringPropertyNames()) {
                String[] arr = name.split("\\.");
                if (arr.length != 2) continue;
                String group = arr[0];
                UsageTracker.Limits old = map.getOrDefault(group, new UsageTracker.Limits(0,0,0));
                int val = Integer.parseInt(p.getProperty(name));
                UsageTracker.Limits nu = old;
                switch (arr[1]) {
                    case "perDay": nu = new UsageTracker.Limits(val, old.perWeek, old.perMonth); break;
                    case "perWeek": nu = new UsageTracker.Limits(old.perDay, val, old.perMonth); break;
                    case "perMonth": nu = new UsageTracker.Limits(old.perDay, old.perWeek, val); break;
                }
                map.put(group, nu);
            }
        }
        // defaults
        map.putIfAbsent("G_FREE_DRINKS", new UsageTracker.Limits(3, 10, 30));
        return map;
    }

    private String fmt(LocalDateTime ldt) {
        return ldt == null ? "?" : ldt.format(timeFmt);
    }

    private void logAndNotify(AccessLog log, Badge badge) {
        db.insertAccessLog(log);

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
