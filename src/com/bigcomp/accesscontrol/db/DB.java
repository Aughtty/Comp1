package com.bigcomp.accesscontrol.db;

import com.bigcomp.accesscontrol.model.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class DB {
    private Properties cfg = new Properties();
    private Connection conn;

    // In-memory caches
    private Map<String, Badge> badges = new HashMap<>();
    private Map<String, Reader> readers = new HashMap<>();
    private Map<String, Resource> resources = new HashMap<>();
    private Map<String, ResourceGroup> groups = new HashMap<>();
    private Map<String, List<String>> badgeProfiles = new HashMap<>();
    private Map<String, List<String>> groupResources = new HashMap<>();
    private Map<String, User> users = new HashMap<>();
    private Map<String, com.bigcomp.accesscontrol.model.Profile> profiles = new HashMap<>();

    public DB() {
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            cfg.load(fis);
        } catch (Exception e) {
            System.err.println("config.properties not found or unreadable, using defaults");
            cfg.setProperty("db.url", "jdbc:mysql://localhost:3306/bigcomp?useSSL=false&serverTimezone=UTC");
            cfg.setProperty("db.user", "root");
            cfg.setProperty("db.password", "123456");
        }
    }

    public void initialize() {
        try {
            connect();
            ensureAccessLogs();
            loadAll();
        } catch (SQLException e) {
            e.printStackTrace();
            // continue: app can run in degraded mode
        }
    }

    private void connect() throws SQLException {
        if (conn != null) return;
        String url = cfg.getProperty("db.url");
        String user = cfg.getProperty("db.user");
        String pass = cfg.getProperty("db.password");
        conn = DriverManager.getConnection(url, user, pass);
        System.out.println("DB connected to: " + url);
    }

    private void ensureAccessLogs() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS AccessLogs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "ts DATETIME, " +
                "badge_id VARCHAR(50), " +
                "reader_id VARCHAR(50), " +
                "resource_id VARCHAR(50), " +
                "result VARCHAR(10), " +
                "message VARCHAR(255)" +
                ")";
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private void loadAll() throws SQLException {
        loadUsers();
        loadBadges();
        loadReaders();
        loadResources();
        loadGroups();
        loadProfiles();
        loadGroupResources();
        loadBadgeProfiles();
    }

    private void loadBadges() throws SQLException {
        badges.clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM Badges")) {
            while (rs.next()) {
                Badge b = new Badge();
                b.setBadgeId(rs.getString("badge_id"));
                b.setUserId(rs.getString("user_id"));
                java.sql.Date exp = rs.getDate("expiration_date");
                b.setExpirationDate(exp == null ? null : exp.toLocalDate());
                b.setActive(rs.getBoolean("is_active"));
                java.sql.Timestamp upd = rs.getTimestamp("last_update_date");
                b.setLastUpdateTime(upd == null ? null : upd.toLocalDateTime());
                String zone = rs.getString("current_zone_id");
                b.setCurrentZoneId(zone != null ? zone : "Z_OUTSIDE"); // default to Z_OUTSIDE if null
                b.setRequiresUpdate(false);
                badges.put(b.getBadgeId(), b);
            }
        }
        System.out.println("Loaded badges: " + badges.size());
        loadBadgeUpdateState();
    }

    private void loadUsers() throws SQLException {
        users.clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM Users")) {
            while (rs.next()) {
                User u = new User();
                u.setUserId(rs.getString("user_id"));
                u.setIdNumber(rs.getString("id_number"));
                u.setFirstName(rs.getString("first_name"));
                u.setLastName(rs.getString("last_name"));
                u.setGender(rs.getString("gender"));
                users.put(u.getUserId(), u);
            }
        }
        System.out.println("Loaded users: " + users.size());
    }

    private void loadReaders() throws SQLException {
        readers.clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM BadgeReaders")) {
            while (rs.next()) {
                Reader r = new Reader();
                r.setReaderId(rs.getString("reader_id"));
                r.setResourceId(rs.getString("resource_id"));
                r.setUiX(rs.getInt("ui_x_coord"));
                r.setUiY(rs.getInt("ui_y_coord"));
                readers.put(r.getReaderId(), r);
            }
        }
        System.out.println("Loaded readers: " + readers.size());
    }

    private void loadResources() throws SQLException {
        resources.clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM Resources")) {
            while (rs.next()) {
                Resource res = new Resource();
                res.setResourceId(rs.getString("resource_id"));
                res.setReaderId(rs.getString("reader_id"));
                res.setName(rs.getString("name"));
                res.setResourceType(rs.getString("resource_type"));
                res.setFromZoneId(rs.getString("from_zone_id"));
                res.setToZoneId(rs.getString("to_zone_id"));
                String state = rs.getString("state");
                res.setControlled(!"UNCONTROLLED".equalsIgnoreCase(state));
                resources.put(res.getResourceId(), res);
            }
        }
        System.out.println("Loaded resources: " + resources.size());
    }

    private void loadGroups() throws SQLException {
        groups.clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM ResourceGroups")) {
            while (rs.next()) {
                ResourceGroup g = new ResourceGroup();
                g.setGroupName(rs.getString("group_name"));
                g.setSecurityLevel(rs.getInt("security_level"));
                g.setDescription(rs.getString("description"));
                groups.put(g.getGroupName(), g);
            }
        }
        System.out.println("Loaded groups: " + groups.size());
    }

    private void loadProfiles() throws SQLException {
        profiles.clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM Profiles")) {
            while (rs.next()) {
                String name = rs.getString("profile_name");
                String path = rs.getString("file_path");
                String desc = rs.getString("description");
                com.bigcomp.accesscontrol.model.Profile p = readProfileFromFile(name, path, desc);
                profiles.put(name, p);
            }
        }
        System.out.println("Loaded profiles: " + profiles.size());
    }

    private void loadGroupResources() throws SQLException {
        groupResources.clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM Group_Resources")) {
            while (rs.next()) {
                String g = rs.getString("group_name");
                String r = rs.getString("resource_id");
                groupResources.computeIfAbsent(g, k -> new ArrayList<>()).add(r);
            }
        }
    }

    private void loadBadgeProfiles() throws SQLException {
        badgeProfiles.clear();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM Badge_Profiles")) {
            while (rs.next()) {
                String b = rs.getString("badge_id");
                String p = rs.getString("profile_name");
                badgeProfiles.computeIfAbsent(b, k -> new ArrayList<>()).add(p);
            }
        }
    }

    private com.bigcomp.accesscontrol.model.Profile readProfileFromFile(String profileName, String path, String desc) {
        String actualPath = (path == null || path.isEmpty()) ? ("data/profiles/" + profileName + ".txt") : path;
        java.io.File f = new java.io.File(actualPath);
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (f.exists()) {
            try {
                lines = java.nio.file.Files.readAllLines(f.toPath());
            } catch (Exception ignored) { }
        }
        com.bigcomp.accesscontrol.model.Profile profile = new com.bigcomp.accesscontrol.model.Profile(profileName);
        if (desc != null) profile.setDescription(desc);
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            if (l.startsWith("description=")) {
                profile.setDescription(l.substring("description=".length()));
            } else if (l.startsWith("right=")) {
                String body = l.substring("right=".length());
                String[] parts = body.split("\\|");
                if (parts.length >= 2) {
                    String group = parts[0].trim();
                    String tf = parts[1].trim();
                    try {
                        profile.addAccessRight(new com.bigcomp.accesscontrol.model.AccessRight(group, tf));
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        }
        // If file missing or empty, seed with a permissive rule to avoid empty profile
        if (profile.getRights().isEmpty()) {
            profile.addAccessRight(new com.bigcomp.accesscontrol.model.AccessRight("G_PUBLIC_ACCESS", "ALL.ALL.ALL.ALL"));
        }
        return profile;
    }

    // Simple getters for caches
    public Optional<Badge> findBadge(String badgeId) { return Optional.ofNullable(badges.get(badgeId)); }
    public Optional<Reader> findReader(String readerId) { return Optional.ofNullable(readers.get(readerId)); }
    public Optional<Resource> findResourceById(String resourceId) { return Optional.ofNullable(resources.get(resourceId)); }
    public Optional<Resource> findResourceByReader(String readerId) {
        for (Resource r : resources.values()) if (readerId.equals(r.getReaderId())) return Optional.of(r);
        return Optional.empty();
    }

    public Optional<String> findGroupForResource(String resourceId) {
        for (Map.Entry<String, List<String>> e : groupResources.entrySet()) {
            if (e.getValue().contains(resourceId)) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    public List<String> getProfilesForBadge(String badgeId) {
        return badgeProfiles.getOrDefault(badgeId, Collections.emptyList());
    }

    public Collection<Reader> getAllReaders() { return readers.values(); }
    public Collection<Badge> getAllBadges() { return badges.values(); }
    public Collection<Resource> getAllResources() { return resources.values(); }
    public Collection<User> getAllUsers() { return users.values(); }
    public Optional<User> findUserById(String userId) { return Optional.ofNullable(users.get(userId)); }
    public Optional<com.bigcomp.accesscontrol.model.Profile> findProfileByName(String name) { return Optional.ofNullable(profiles.get(name)); }
    public java.util.Set<String> getAllGroupNames() { return groups.keySet(); }

    // Logging access
    public void insertAccessLog(AccessLog log) {
        if (conn == null) return;
        String sql = "INSERT INTO AccessLogs (ts, badge_id, reader_id, resource_id, result, message) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(log.getTimestamp()));
            ps.setString(2, log.getBadgeId());
            ps.setString(3, log.getReaderId());
            ps.setString(4, log.getResourceId());
            ps.setString(5, log.getResult());
            ps.setString(6, log.getMessage());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Admin actions
    public java.util.List<AccessLog> getRecentLogs(int limit) {
        java.util.List<AccessLog> out = new java.util.ArrayList<>();
        if (conn == null) return out;
        String sql = "SELECT ts, badge_id, reader_id, resource_id, result, message FROM AccessLogs ORDER BY ts DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AccessLog l = new AccessLog();
                    l.setTimestamp(rs.getTimestamp(1).toLocalDateTime());
                    l.setBadgeId(rs.getString(2));
                    l.setReaderId(rs.getString(3));
                    l.setResourceId(rs.getString(4));
                    l.setResult(rs.getString(5));
                    l.setMessage(rs.getString(6));
                    out.add(l);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return out;
    }

    public int getUsageCountToday(String badgeId, String groupName) {
        if (conn == null) return 0;
        String sql = "SELECT usage_count, last_usage_date FROM UsageCounters WHERE badge_id=? AND group_name=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, badgeId);
            ps.setString(2, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Date d = rs.getDate(2);
                    int cnt = rs.getInt(1);
                    if (d != null && d.toLocalDate().equals(java.time.LocalDate.now())) return cnt;
                    return 0;
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public void incrementUsageCount(String badgeId, String groupName) {
        if (conn == null) return;
        String select = "SELECT usage_count, last_usage_date FROM UsageCounters WHERE badge_id=? AND group_name=?";
        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, badgeId);
            ps.setString(2, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                java.sql.Date today = java.sql.Date.valueOf(java.time.LocalDate.now());
                if (rs.next()) {
                    int cnt = rs.getInt(1);
                    java.sql.Date d = rs.getDate(2);
                    if (d != null && d.equals(today)) {
                        // increment
                        try (PreparedStatement up = conn.prepareStatement("UPDATE UsageCounters SET usage_count=?, last_usage_date=? WHERE badge_id=? AND group_name=?")) {
                            up.setInt(1, cnt + 1);
                            up.setDate(2, today);
                            up.setString(3, badgeId);
                            up.setString(4, groupName);
                            up.executeUpdate();
                        }
                    } else {
                        // reset to 1
                        try (PreparedStatement up = conn.prepareStatement("UPDATE UsageCounters SET usage_count=1, last_usage_date=? WHERE badge_id=? AND group_name=?")) {
                            up.setDate(1, today);
                            up.setString(2, badgeId);
                            up.setString(3, groupName);
                            up.executeUpdate();
                        }
                    }
                } else {
                    try (PreparedStatement ins = conn.prepareStatement("INSERT INTO UsageCounters (badge_id, group_name, usage_count, last_usage_date) VALUES (?,?,?,?)")) {
                        ins.setString(1, badgeId);
                        ins.setString(2, groupName);
                        ins.setInt(3, 1);
                        ins.setDate(4, java.sql.Date.valueOf(java.time.LocalDate.now()));
                        ins.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public boolean updateBadgeCurrentZone(String badgeId, String zoneId) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE Badges SET current_zone_id=? WHERE badge_id=?")) {
            ps.setString(1, zoneId);
            ps.setString(2, badgeId);
            int n = ps.executeUpdate();
            if (n>0) {
                // update cache
                Badge b = badges.get(badgeId);
                if (b != null) b.setCurrentZoneId(zoneId);
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean revokeBadge(String badgeId) {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE Badges SET is_active=0 WHERE badge_id=?")) {
            ps.setString(1, badgeId);
            int n = ps.executeUpdate();
            if (n>0) {
                // refresh cache
                initialize();
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // Profile persistence (file-backed, uses Profiles table for metadata)
    public boolean saveProfile(com.bigcomp.accesscontrol.model.Profile profile) {
        if (profile == null || conn == null) return false;
        String path = "data/profiles/" + profile.getProfileName() + ".txt";
        java.io.File f = new java.io.File(path);
        f.getParentFile().mkdirs();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(f))) {
            if (profile.getDescription() != null) pw.println("description=" + profile.getDescription());
            for (com.bigcomp.accesscontrol.model.AccessRight r : profile.getRights()) {
                pw.println("right=" + r.getGroupName() + "|" + r.getTimeRuleText());
            }
        } catch (Exception e) { e.printStackTrace(); return false; }

        String sql = "INSERT INTO Profiles (profile_name, file_path, description) VALUES (?,?,?) " +
                "ON DUPLICATE KEY UPDATE file_path=VALUES(file_path), description=VALUES(description)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profile.getProfileName());
            ps.setString(2, path);
            ps.setString(3, profile.getDescription());
            ps.executeUpdate();
            profiles.put(profile.getProfileName(), profile);
            return true;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean deleteProfile(String profileName) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Profiles WHERE profile_name=?")) {
            ps.setString(1, profileName);
            ps.executeUpdate();
            profiles.remove(profileName);
            // clean badge assignments cache
            badgeProfiles.values().forEach(list -> list.removeIf(p -> p.equals(profileName)));
            java.io.File f = new java.io.File("data/profiles/" + profileName + ".txt");
            if (f.exists()) f.delete();
            return true;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // User CRUD used by editor panels
    public boolean saveUser(User user) {
        if (user == null || conn == null) return false;
        String sql = "INSERT INTO Users (user_id, id_number, first_name, last_name, gender) VALUES (?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE id_number=VALUES(id_number), first_name=VALUES(first_name), last_name=VALUES(last_name), gender=VALUES(gender)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUserId());
            ps.setString(2, user.getIdNumber());
            ps.setString(3, user.getFirstName());
            ps.setString(4, user.getLastName());
            ps.setString(5, user.getGender());
            ps.executeUpdate();
            users.put(user.getUserId(), user);
            return true;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public boolean deleteUser(String userId) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Users WHERE user_id=?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
            users.remove(userId);
            return true;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    // Badge update state persisted to local file (no DB schema change)
    private void loadBadgeUpdateState() {
        java.io.File f = new java.io.File("data/badge_updates.csv");
        if (!f.exists()) return;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] arr = line.split(",");
                if (arr.length < 5) continue;
                Badge b = badges.get(arr[0]);
                if (b == null) continue;
                b.setRequiresUpdate(Boolean.parseBoolean(arr[1]));
                b.setUpdateDueDate(parseLdt(arr[2]));
                b.setUpdateGracePeriodEnd(parseLdt(arr[3]));
                b.setLastUpdateTime(parseLdt(arr[4]));
            }
        } catch (Exception ignored) { }
    }

    private java.time.LocalDateTime parseLdt(String s) {
        if (s == null || s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try { return java.time.LocalDateTime.parse(s); } catch (Exception e) { return null; }
    }

    private void saveBadgeUpdateState() {
        java.io.File f = new java.io.File("data/badge_updates.csv");
        f.getParentFile().mkdirs();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(f))) {
            for (Badge b : badges.values()) {
                pw.printf("%s,%s,%s,%s,%s%n",
                        b.getBadgeId(),
                        Boolean.toString(b.isRequiresUpdate()),
                        b.getUpdateDueDate(),
                        b.getUpdateGracePeriodEnd(),
                        b.getLastUpdateTime());
            }
        } catch (Exception ignored) { }
    }

    public boolean updateBadgeUpdateStatus(Badge badge) {
        if (badge == null) return false;
        badges.put(badge.getBadgeId(), badge);
        saveBadgeUpdateState();
        if (conn != null) {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE Badges SET last_update_date=? WHERE badge_id=?")) {
                java.time.LocalDateTime ldt = badge.getLastUpdateTime();
                ps.setTimestamp(1, ldt == null ? null : java.sql.Timestamp.valueOf(ldt));
                ps.setString(2, badge.getBadgeId());
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return true;
    }

    // Toggle controlled/uncontrolled for all resources in a group (uses existing Resources.state field)
    public boolean setGroupControlled(String groupName, boolean controlled) {
        java.util.List<String> resIds = groupResources.getOrDefault(groupName, java.util.Collections.emptyList());
        if (resIds.isEmpty() || conn == null) return false;
        String state = controlled ? "CONTROLLED" : "UNCONTROLLED";
        try (PreparedStatement ps = conn.prepareStatement("UPDATE Resources SET state=? WHERE resource_id=?")) {
            for (String rid : resIds) {
                ps.setString(1, state);
                ps.setString(2, rid);
                ps.addBatch();
            }
            ps.executeBatch();
            for (String rid : resIds) {
                Resource r = resources.get(rid);
                if (r != null) r.setControlled(controlled);
            }
            return true;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }
}
