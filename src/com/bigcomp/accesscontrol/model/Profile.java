package com.bigcomp.accesscontrol.model;

import java.util.*;

public class Profile {
    private final String profileName;
    private String description;
    private final List<AccessRight> rights = new ArrayList<>();

    public Profile(String profileName) {
        if (profileName == null || profileName.isEmpty()) {
            throw new IllegalArgumentException("profileName cannot be empty");
        }
        this.profileName = profileName;
    }

    public String getProfileName() { return profileName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<AccessRight> getRights() { return rights; }

    public void addAccessRight(AccessRight right) { if (right != null) rights.add(right); }
}
