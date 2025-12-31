package com.bigcomp.accesscontrol.model;

public class Reader {
    private String readerId;
    private String resourceId;
    private int uiX, uiY;
    
    // Badge update mode: 0 = normal swipe, 1 = badge held for update
    private int updateMode = 0;

    public String getReaderId() { return readerId; }
    public void setReaderId(String readerId) { this.readerId = readerId; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public int getUiX() { return uiX; }
    public void setUiX(int uiX) { this.uiX = uiX; }
    public int getUiY() { return uiY; }
    public void setUiY(int uiY) { this.uiY = uiY; }
    
    public int getUpdateMode() { return updateMode; }
    public void setUpdateMode(int updateMode) { this.updateMode = updateMode; }
}