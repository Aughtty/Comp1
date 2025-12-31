package com.bigcomp.accesscontrol.util;

import java.io.FileInputStream;
import java.util.Properties;

/**
 * Loads per-group usage limits from config.properties with keys like:
 * limit.<GROUP_NAME>.daily=5
 * Missing or invalid entries default to 0 (no limit enforced here).
 */
public final class LimitsConfig {
    private static final Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
        } catch (Exception e) {
            // If config not found, fall back to defaults (no limits here)
        }
    }

    private LimitsConfig() {}

    /**
     * Get daily limit for a resource group from config.properties
     * @param groupName resource group name (e.g., G_FREE_DRINKS)
     * @return limit >= 0; 0 means no limit enforced here
     */
    public static int getDailyLimit(String groupName) {
        if (groupName == null || groupName.isEmpty()) return 0;
        String key = "limit." + groupName + ".daily";
        String val = props.getProperty(key);
        if (val == null) return 0;
        try {
            int parsed = Integer.parseInt(val.trim());
            return Math.max(parsed, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
