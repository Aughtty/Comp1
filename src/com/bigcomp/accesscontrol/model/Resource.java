package com.bigcomp.accesscontrol.model;

public class Resource {
    private String resourceId;
    private String readerId;
    private String name;
    private String resourceType;
    private String fromZoneId;
    private String toZoneId;
    private boolean controlled;  // true = requires badge check, false = always accessible

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getReaderId() { return readerId; }
    public void setReaderId(String readerId) { this.readerId = readerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getFromZoneId() { return fromZoneId; }
    public void setFromZoneId(String fromZoneId) { this.fromZoneId = fromZoneId; }
    public String getToZoneId() { return toZoneId; }
    public void setToZoneId(String toZoneId) { this.toZoneId = toZoneId; }
    
    public boolean isControlled() { return controlled; }
    public void setControlled(boolean controlled) { this.controlled = controlled; }
}