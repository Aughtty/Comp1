package com.bigcomp.accesscontrol.model;

import com.bigcomp.accesscontrol.util.TimeFilter;

/**
 * AccessRight represents a permission to access a resource group during certain times.
 * Example: (G_PUBLIC_ACCESS, 2025.July,August.Monday-Friday.8:00-12:00,14:00-17:00)
 */
public class AccessRight {
    private String groupName;
    private TimeFilter timeFilter;
    private String timeFilterRule;  // Store original rule for serialization

    public AccessRight(String groupName, String timeFilterRule) throws IllegalArgumentException {
        this.groupName = groupName;
        this.timeFilterRule = timeFilterRule;
        this.timeFilter = new TimeFilter(timeFilterRule);
    }

    public String getGroupName() {
        return groupName;
    }

    public TimeFilter getTimeFilter() {
        return timeFilter;
    }

    public String getTimeFilterRule() {
        return timeFilterRule;
    }

    /**
     * Check if this access right is valid at the given time
     */
    public boolean isValidAtTime(java.time.LocalDateTime dateTime) {
        return timeFilter.matches(dateTime);
    }

    @Override
    public String toString() {
        return groupName + " (" + timeFilterRule + ")";
    }
}
