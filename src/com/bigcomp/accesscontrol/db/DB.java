package com.bigcomp.accesscontrol.db;

import com.bigcomp.accesscontrol.model.*;
import com.bigcomp.accesscontrol.util.ProfileManager;

import java.io.FileInputStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class DB {
    private Properties cfg = new Properties();
    private Connection conn;
    private ProfileManager profileManager;

    // In-memory caches
    private Map<String, Badge> badges = new HashMap<>();
    private Map<String, Reader> readers = new HashMap<>();
    private Map<String, Resource> resources = new HashMap<>();
    private Map<String, ResourceGroup> groups = new HashMap<>();
    private Map<String, List<String>> badgeProfiles = new HashMap<>();
    private Map<String, List<String>> groupResources = new HashMap<>();
    private Map<String, User> users = new HashMap<>();

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
            // Initialize profile manager with default profiles
            profileManager = new ProfileManager();
            profileManager.initializeDefaultProfiles();
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
                String zone = rs.getString("current_zone_id");
                b.setCurrentZoneId(zone != null ? zone : "Z_OUTSIDE"); // default to Z_OUTSIDE if null
                
                // Load update-related fields if they exist in the table
                try {
                    java.sql.Timestamp updateDue = rs.getTimestamp("update_due_date");
                    if (updateDue != null) {
                        b.setUpdateDueDate(updateDue.toLocalDateTime());
                    }
                    java.sql.Timestamp updateGraceEnd = rs.getTimestamp("update_grace_period_end");
                    if (updateGraceEnd != null) {
                        b.setUpdateGracePeriodEnd(updateGraceEnd.toLocalDateTime());
                    }
                    b.setRequiresUpdate(rs.getBoolean("requires_update"));
                    java.sql.Timestamp lastUpdate = rs.getTimestamp("last_update_time");
                    if (lastUpdate != null) {
                        b.setLastUpdateTime(lastUpdate.toLocalDateTime());
                    }
                } catch (SQLException e) {
                    // Column doesn't exist, ignore for backward compatibility
                }

                badges.put(b.getBadgeId(), b);
            }
        }
        System.out.println("Loaded badges: " + badges.size());
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
                
                // Load controlled status if it exists
                try {
                    res.setControlled(rs.getBoolean("is_controlled"));
                } catch (SQLException e) {
                    // Column doesn't exist, default to controlled
                    res.setControlled(true);
                }
                
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

    public ProfileManager getProfileManager() {
        if (profileManager == null) {
            profileManager = new ProfileManager();
            profileManager.initializeDefaultProfiles();
        }
        return profileManager;
    }

    // ====== New methods for advanced access control ======

    /**
     * Get the most recent access history for a badge
     * Used for precedence checks
     */
    public Optional<AccessHistory> getMostRecentAccess(String badgeId) {
        if (conn == null) return Optional.empty();
        String sql = "SELECT badge_id, from_zone_id, to_zone_id, resource_id, access_time FROM AccessHistory " +
                     "WHERE badge_id=? AND result='GRANTED' ORDER BY access_time DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, badgeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AccessHistory ah = new AccessHistory(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getTimestamp(5).toLocalDateTime()
                    );
                    return Optional.of(ah);
                }
            }
        } catch (SQLException e) { 
            // Table may not exist yet
            System.err.println("AccessHistory table not available: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Record access history for precedence checking
     */
    public void recordAccessHistory(String badgeId, String fromZoneId, String toZoneId, String resourceId, String result) {
        if (conn == null) return;
        String sql = "INSERT INTO AccessHistory (badge_id, from_zone_id, to_zone_id, resource_id, result, access_time) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, badgeId);
            ps.setString(2, fromZoneId);
            ps.setString(3, toZoneId);
            ps.setString(4, resourceId);
            ps.setString(5, result);
            ps.setTimestamp(6, Timestamp.valueOf(java.time.LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            // Table may not exist - silent fail
            System.err.println("Could not record access history: " + e.getMessage());
        }
    }

    /**
     * Get access count within a time window (daily/weekly/monthly)
     */
    public int getAccessCountInWindow(String badgeId, String groupName, String window) {
        // window: "DAILY", "WEEKLY", "MONTHLY"
        if (conn == null) return 0;
        
        String sqlSnippet = "";
        if ("DAILY".equalsIgnoreCase(window)) {
            sqlSnippet = "AND DATE(access_time) = CURDATE()";
        } else if ("WEEKLY".equalsIgnoreCase(window)) {
            sqlSnippet = "AND YEARWEEK(access_time) = YEARWEEK(NOW())";
        } else if ("MONTHLY".equalsIgnoreCase(window)) {
            sqlSnippet = "AND YEAR(access_time) = YEAR(NOW()) AND MONTH(access_time) = MONTH(NOW())";
        }
        
        String sql = "SELECT COUNT(*) FROM AccessLogs al " +
                     "JOIN Group_Resources gr ON al.resource_id = gr.resource_id " +
                     "WHERE al.badge_id=? AND gr.group_name=? AND al.result='GRANTED' " + sqlSnippet;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, badgeId);
            ps.setString(2, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    /**
     * Get maximum usage allowed for a resource group
     */
    public int getMaxUsageForGroup(String groupName) {
        if (conn == null) return Integer.MAX_VALUE;
        String sql = "SELECT max_daily_usage FROM ResourceGroups WHERE group_name=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int max = rs.getInt(1);
                    return max > 0 ? max : Integer.MAX_VALUE;
                }
            }
        } catch (SQLException e) {
            // Column may not exist
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Add or update a user
     */
    public boolean saveUser(User user) {
        if (conn == null || user == null) return false;
        String checkSql = "SELECT user_id FROM Users WHERE user_id=?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setString(1, user.getUserId());
            ResultSet rs = checkPs.executeQuery();
            boolean exists = rs.next();
            rs.close();

            if (exists) {
                // UPDATE
                String updateSql = "UPDATE Users SET first_name=?, last_name=?, gender=?, id_number=? WHERE user_id=?";
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setString(1, user.getFirstName());
                    updatePs.setString(2, user.getLastName());
                    updatePs.setString(3, user.getGender());
                    updatePs.setString(4, user.getIdNumber());
                    updatePs.setString(5, user.getUserId());
                    updatePs.executeUpdate();
                }
            } else {
                // INSERT
                String insertSql = "INSERT INTO Users (user_id, first_name, last_name, gender, id_number) VALUES (?,?,?,?,?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setString(1, user.getUserId());
                    insertPs.setString(2, user.getFirstName());
                    insertPs.setString(3, user.getLastName());
                    insertPs.setString(4, user.getGender());
                    insertPs.setString(5, user.getIdNumber());
                    insertPs.executeUpdate();
                }
            }
            
            // Update cache
            users.put(user.getUserId(), user);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Delete a user
     */
    public boolean deleteUser(String userId) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Users WHERE user_id=?")) {
            ps.setString(1, userId);
            int n = ps.executeUpdate();
            if (n > 0) {
                users.remove(userId);
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    /**
     * Save a profile to database
     */
    public boolean saveProfile(Profile profile) {
        if (conn == null || profile == null) return false;
        String profileName = profile.getProfileName();
        
        // Check if exists
        String checkSql = "SELECT profile_name FROM Profiles WHERE profile_name=?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setString(1, profileName);
            ResultSet rs = checkPs.executeQuery();
            boolean exists = rs.next();
            rs.close();

            if (exists) {
                // UPDATE
                String updateSql = "UPDATE Profiles SET description=? WHERE profile_name=?";
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setString(1, profile.getDescription());
                    updatePs.setString(2, profileName);
                    updatePs.executeUpdate();
                }
            } else {
                // INSERT
                String insertSql = "INSERT INTO Profiles (profile_name, description) VALUES (?,?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setString(1, profileName);
                    insertPs.setString(2, profile.getDescription());
                    insertPs.executeUpdate();
                }
            }

            // Update badge_profile associations
            deleteBadgeProfileAssociations(profileName);
            for (AccessRight right : profile.getAccessRights()) {
                String assocSql = "INSERT INTO BadgeProfile_AccessRights (profile_name, group_name, time_filter) VALUES (?,?,?)";
                try (PreparedStatement assocPs = conn.prepareStatement(assocSql)) {
                    assocPs.setString(1, profileName);
                    assocPs.setString(2, right.getGroupName());
                    assocPs.setString(3, right.getTimeFilterRule());
                    assocPs.executeUpdate();
                }
            }
            
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Delete a profile
     */
    public boolean deleteProfile(String profileName) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Profiles WHERE profile_name=?")) {
            ps.setString(1, profileName);
            int n = ps.executeUpdate();
            if (n > 0) {
                deleteBadgeProfileAssociations(profileName);
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    /**
     * Delete all access rights for a profile
     */
    private void deleteBadgeProfileAssociations(String profileName) {
        if (conn == null) return;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM BadgeProfile_AccessRights WHERE profile_name=?")) {
            ps.setString(1, profileName);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Table may not exist yet
        }
    }

    /**
     * Assign a profile to a badge
     */
    public boolean assignProfileToBadge(String badgeId, String profileName) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO Badge_Profiles (badge_id, profile_name) VALUES (?,?)")) {
            ps.setString(1, badgeId);
            ps.setString(2, profileName);
            ps.executeUpdate();
            // Update cache
            badgeProfiles.computeIfAbsent(badgeId, k -> new ArrayList<>()).add(profileName);
            return true;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    /**
     * Remove profile from badge
     */
    public boolean removeProfileFromBadge(String badgeId, String profileName) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Badge_Profiles WHERE badge_id=? AND profile_name=?")) {
            ps.setString(1, badgeId);
            ps.setString(2, profileName);
            int n = ps.executeUpdate();
            if (n > 0) {
                List<String> profs = badgeProfiles.get(badgeId);
                if (profs != null) profs.remove(profileName);
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    /**
     * Update badge update status fields
     */
    public boolean updateBadgeUpdateStatus(Badge badge) {
        if (conn == null || badge == null) return false;
        String sql = "UPDATE Badges SET requires_update=?, update_due_date=?, update_grace_period_end=?, last_update_time=? WHERE badge_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, badge.isRequiresUpdate());
            ps.setTimestamp(2, badge.getUpdateDueDate() != null ? Timestamp.valueOf(badge.getUpdateDueDate()) : null);
            ps.setTimestamp(3, badge.getUpdateGracePeriodEnd() != null ? Timestamp.valueOf(badge.getUpdateGracePeriodEnd()) : null);
            ps.setTimestamp(4, badge.getLastUpdateTime() != null ? Timestamp.valueOf(badge.getLastUpdateTime()) : null);
            ps.setString(5, badge.getBadgeId());
            int n = ps.executeUpdate();
            return n > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }    /**
     * Create a new resource
     */
    public boolean createResource(Resource resource) {
        if (conn == null || resource == null) return false;
        String sql = "INSERT INTO Resources (resource_id, reader_id, name, resource_type, from_zone_id, to_zone_id, is_controlled) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resource.getResourceId());
            ps.setString(2, resource.getReaderId());
            ps.setString(3, resource.getName());
            ps.setString(4, resource.getResourceType());
            ps.setString(5, resource.getFromZoneId());
            ps.setString(6, resource.getToZoneId());
            ps.setBoolean(7, resource.isControlled());
            int n = ps.executeUpdate();
            if (n > 0) {
                resources.put(resource.getResourceId(), resource);
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    /**
     * Update a resource
     */
    public boolean updateResource(Resource resource) {
        if (conn == null || resource == null) return false;
        String sql = "UPDATE Resources SET reader_id=?, name=?, resource_type=?, from_zone_id=?, to_zone_id=?, is_controlled=? WHERE resource_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resource.getReaderId());
            ps.setString(2, resource.getName());
            ps.setString(3, resource.getResourceType());
            ps.setString(4, resource.getFromZoneId());
            ps.setString(5, resource.getToZoneId());
            ps.setBoolean(6, resource.isControlled());
            ps.setString(7, resource.getResourceId());
            int n = ps.executeUpdate();
            if (n > 0) {
                resources.put(resource.getResourceId(), resource);
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    /**
     * Delete a resource
     */
    public boolean deleteResource(String resourceId) {
        if (conn == null) return false;
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Resources WHERE resource_id=?")) {
            ps.setString(1, resourceId);
            int n = ps.executeUpdate();
            if (n > 0) {
                resources.remove(resourceId);
                return true;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }
}
