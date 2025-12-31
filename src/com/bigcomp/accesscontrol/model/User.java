package com.bigcomp.accesscontrol.model;

public class User {
    private String userId;
    private String idNumber;
    private String firstName;
    private String lastName;
    private String gender;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getFullName() {
        return (firstName != null ? firstName : "") + (lastName != null ? ":" + lastName : "");
    }
}
