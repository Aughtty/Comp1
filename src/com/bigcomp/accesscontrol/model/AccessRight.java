package com.bigcomp.accesscontrol.model;

/** Pair of resource group and time filter */
public class AccessRight {
    private final String groupName;
    private final TimeFilter timeFilter;
    private final String timeRuleText;

    public AccessRight(String groupName, String timeRule) {
        this(groupName, TimeFilter.parse(timeRule), timeRule);
    }

    public AccessRight(String groupName, TimeFilter filter) {
        this(groupName, filter, "ALL.ALL.ALL.ALL");
    }

    public AccessRight(String groupName, TimeFilter filter, String rawRule) {
        if (groupName == null || groupName.isEmpty()) {
            throw new IllegalArgumentException("groupName cannot be empty");
        }
        this.groupName = groupName;
        this.timeFilter = filter == null ? TimeFilter.parse("ALL.ALL.ALL.ALL") : filter;
        this.timeRuleText = (rawRule == null || rawRule.isEmpty()) ? "ALL.ALL.ALL.ALL" : rawRule;
    }

    public String getGroupName() { return groupName; }
    public TimeFilter getTimeFilter() { return timeFilter; }
    public String getTimeRuleText() { return timeRuleText; }
}
