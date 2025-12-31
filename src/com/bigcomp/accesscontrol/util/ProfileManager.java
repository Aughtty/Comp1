package com.bigcomp.accesscontrol.util;

import com.bigcomp.accesscontrol.model.AccessRight;
import com.bigcomp.accesscontrol.model.Profile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * ProfileManager loads and manages Profile configurations from files.
 * Profile file format (text-based, similar to properties):
 * 
 * Example profile file content:
 * # Comment: Employee standard profile
 * P_EMPLOYEE.G_PUBLIC_ACCESS = 2025.ALL.Monday-Friday.8:00-18:00
 * P_EMPLOYEE.G_FREE_DRINKS = 2025.ALL.ALL.EXCEPT 12:00-14:00
 * 
 * Or admin profile:
 * P_ADMIN.G_PUBLIC_ACCESS = ALL.ALL.ALL.ALL
 * P_ADMIN.G_TOP_SECRET = ALL.ALL.ALL.ALL
 * P_ADMIN.G_FREE_DRINKS = ALL.ALL.ALL.ALL
 */
public class ProfileManager {
    private Map<String, Profile> profiles = new HashMap<>();

    public ProfileManager() {
    }

    /**
     * Load profiles from a file
     * File format: PROFILE_NAME.GROUP_NAME = TIME_FILTER_RULE
     */
    public void loadFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            System.err.println("Profile file not found: " + filePath);
            return;
        }

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                try {
                    parseAndAddAccessRight(line);
                } catch (IllegalArgumentException e) {
                    System.err.println("Error parsing profile line: " + line + " - " + e.getMessage());
                }
            }
        }
    }

    /**
     * Parse a line like "P_EMPLOYEE.G_PUBLIC_ACCESS = 2025.ALL.Monday-Friday.8:00-18:00"
     */
    private void parseAndAddAccessRight(String line) throws IllegalArgumentException {
        String[] parts = line.split("=");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Profile line must have format: PROFILE_NAME.GROUP_NAME = TIME_FILTER_RULE");
        }

        String leftSide = parts[0].trim();
        String timeFilterRule = parts[1].trim();

        String[] nameparts = leftSide.split("\\.");
        if (nameparts.length != 2) {
            throw new IllegalArgumentException("Left side must have format: PROFILE_NAME.GROUP_NAME");
        }

        String profileName = nameparts[0].trim();
        String groupName = nameparts[1].trim();

        // Get or create profile
        Profile profile = profiles.computeIfAbsent(profileName, Profile::new);

        // Add access right
        try {
            profile.addAccessRight(groupName, timeFilterRule);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid time filter rule: " + timeFilterRule + " - " + e.getMessage());
        }
    }

    /**
     * Get a profile by name
     */
    public Profile getProfile(String profileName) {
        return profiles.get(profileName);
    }

    /**
     * Register a profile directly (useful for programmatic setup)
     */
    public void registerProfile(Profile profile) {
        profiles.put(profile.getProfileName(), profile);
    }

    /**
     * Get all registered profiles
     */
    public Collection<Profile> getAllProfiles() {
        return new ArrayList<>(profiles.values());
    }

    /**
     * Create a default profile if it doesn't exist
     */
    public Profile createDefaultProfile(String profileName) {
        Profile p = new Profile(profileName);
        profiles.put(profileName, p);
        return p;
    }

    /**
     * Initialize default built-in profiles
     * This is called to set up P_ADMIN and P_EMPLOYEE profiles for the demo
     */
    public void initializeDefaultProfiles() {
        // P_ADMIN: Full access to all groups, all times
        Profile pAdmin = new Profile("P_ADMIN");
        try {
            pAdmin.addAccessRight("G_PUBLIC_ACCESS", "ALL.ALL.ALL.ALL");
            pAdmin.addAccessRight("G_TOP_SECRET", "ALL.ALL.ALL.ALL");
            pAdmin.addAccessRight("G_FREE_DRINKS", "ALL.ALL.ALL.ALL");
        } catch (Exception e) {
            System.err.println("Error initializing P_ADMIN profile: " + e.getMessage());
        }
        profiles.put("P_ADMIN", pAdmin);

        // P_EMPLOYEE: Limited access
        Profile pEmployee = new Profile("P_EMPLOYEE");
        try {
            pEmployee.addAccessRight("G_PUBLIC_ACCESS", "ALL.ALL.Monday-Friday.8:00-18:00");
            pEmployee.addAccessRight("G_FREE_DRINKS", "ALL.ALL.ALL.EXCEPT 12:00-14:00");
        } catch (Exception e) {
            System.err.println("Error initializing P_EMPLOYEE profile: " + e.getMessage());
        }
        profiles.put("P_EMPLOYEE", pEmployee);
    }

    public static void main(String[] args) {
        ProfileManager pm = new ProfileManager();
        pm.initializeDefaultProfiles();

        Profile pAdmin = pm.getProfile("P_ADMIN");
        if (pAdmin != null) {
            System.out.println("P_ADMIN profile loaded with access rights:");
            for (var right : pAdmin.getAccessRights()) {
                System.out.println("  - " + right.getGroupName());
            }
        }
    }
}
