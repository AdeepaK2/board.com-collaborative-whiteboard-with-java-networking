package org.example.model;

import java.io.Serializable;

/**
 * Represents a drawing message that contains information about drawing operations
 */
public class DrawingMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum MessageType {
        DRAW, CLEAR, USER_JOIN, USER_LEAVE, TEXT, ERASE
    }
    
    private MessageType type;
    private String username;
    private int x1, y1, x2, y2;
    private String color;
    private int strokeWidth;
    private String text;
    private long timestamp;
    
    public DrawingMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public DrawingMessage(MessageType type, String username) {
        this();
        this.type = type;
        this.username = username;
    }
    
    // Drawing constructor
    public DrawingMessage(MessageType type, String username, int x1, int y1, int x2, int y2, String color, int strokeWidth) {
        this(type, username);
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.color = color;
        this.strokeWidth = strokeWidth;
    }
    
    // Text constructor
    public DrawingMessage(MessageType type, String username, int x, int y, String text, String color) {
        this(type, username);
        this.x1 = x;
        this.y1 = y;
        this.text = text;
        this.color = color;
    }
    
    // Getters and Setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public int getX1() { return x1; }
    public void setX1(int x1) { this.x1 = x1; }
    
    public int getY1() { return y1; }
    public void setY1(int y1) { this.y1 = y1; }
    
    public int getX2() { return x2; }
    public void setX2(int x2) { this.x2 = x2; }
    
    public int getY2() { return y2; }
    public void setY2(int y2) { this.y2 = y2; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public int getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(int strokeWidth) { this.strokeWidth = strokeWidth; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public String toString() {
        return "DrawingMessage{" +
                "type=" + type +
                ", username='" + username + '\'' +
                ", x1=" + x1 +
                ", y1=" + y1 +
                ", x2=" + x2 +
                ", y2=" + y2 +
                ", color='" + color + '\'' +
                ", strokeWidth=" + strokeWidth +
                ", text='" + text + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}