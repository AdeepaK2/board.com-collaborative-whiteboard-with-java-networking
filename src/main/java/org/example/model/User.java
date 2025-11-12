package org.example.model;

import java.io.Serializable;

/**
 * Represents a user in the whiteboard application
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String username;
    private String ipAddress;
    private long joinTime;
    private boolean active;
    
    public User() {
        this.joinTime = System.currentTimeMillis();
        this.active = true;
    }
    
    public User(String username, String ipAddress) {
        this();
        this.username = username;
        this.ipAddress = ipAddress;
    }
    
    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public long getJoinTime() { return joinTime; }
    public void setJoinTime(long joinTime) { this.joinTime = joinTime; }
    
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }


    //FOR CHAT
    private long lastMessageTime;

    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        User user = (User) obj;
        return username != null ? username.equals(user.username) : user.username == null;
    }
    
    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", joinTime=" + joinTime +
                ", active=" + active +
                '}';
    }
}