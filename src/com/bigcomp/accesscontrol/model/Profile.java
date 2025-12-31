package com.bigcomp.accesscontrol.model;

import java.util.*;

/**
 * Profile defines access rights for a user/badge.
 * A Profile is a list of AccessRight entries.
 * Example profile might grant access to:
 * - G_PUBLIC_ACCESS at 2025.ALL.Monday-Friday.8:00-18:00
 * - G_FREE_DRINKS at 2025.ALL.ALL.EXCEPT 12:00-14:00
 * - G_TOP_SECRET at ALL.ALL.Monday-Friday.9:00-17:00
 */
public class Profile {
    private String profileName;
    private String description;
    private List<AccessRight> accessRights;

    public Profile(String profileName) {
        this.profileName = profileName;
        this.accessRights = new ArrayList<>();
    }

    public String getProfileName() {
        return profileName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void addAccessRight(AccessRight right) {
        if (right != null) {
            accessRights.add(right);
        }
    }

    public void addAccessRight(String groupName, String timeFilterRule) throws IllegalArgumentException {
        accessRights.add(new AccessRight(groupName, timeFilterRule));
    }

    public List<AccessRight> getAccessRights() {
        return new ArrayList<>(accessRights);
    }

    /**
     * Check if this profile grants access to a given resource group at the given time.
     * @param groupName The resource group to check access for
     * @param dateTime The time to check (defaults to current time if null)
     * @return true if any AccessRight in this profile grants access to the group at the given time
     */
    public boolean grantsAccess(String groupName, java.time.LocalDateTime dateTime) {
        if (dateTime == null) {
            dateTime = java.time.LocalDateTime.now();
        }
        for (AccessRight right : accessRights) {
            if (right.getGroupName().equals(groupName) && right.isValidAtTime(dateTime)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return profileName + " (" + accessRights.size() + " access rights)";
    }
}
