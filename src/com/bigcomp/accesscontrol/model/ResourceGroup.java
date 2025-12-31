package com.bigcomp.accesscontrol.model;

public class ResourceGroup {
    private String groupName;
    private int securityLevel;
    private String description;

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public int getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(int securityLevel) { this.securityLevel = securityLevel; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}