package org.example.model;

import java.io.Serializable;

/**
 * Represents a chat message in a room
 */
public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum MessageType {
        CHAT,           // Regular chat message
        USER_JOINED,    // User joined the room notification
        USER_LEFT,      // User left the room notification
        SYSTEM          // System message
    }
    
    private MessageType type;
    private String username;
    private String message;
    private String roomId;
    private long timestamp;
    
    public ChatMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public ChatMessage(MessageType type, String username, String message, String roomId) {
        this();
        this.type = type;
        this.username = username;
        this.message = message;
        this.roomId = roomId;
    }
    
    // Getters and Setters
    public MessageType getType() {
        return type;
    }
    
    public void setType(MessageType type) {
        this.type = type;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "ChatMessage{" +
                "type=" + type +
                ", username='" + username + '\'' +
                ", message='" + message + '\'' +
                ", roomId='" + roomId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
