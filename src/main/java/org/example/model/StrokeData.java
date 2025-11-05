package org.example.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a freehand drawing stroke
 */
public class StrokeData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private List<DrawPoint> points;
    private String username;
    
    public StrokeData() {
        this.points = new ArrayList<>();
    }
    
    public StrokeData(String id, String username) {
        this();
        this.id = id;
        this.username = username;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public List<DrawPoint> getPoints() { return points; }
    public void setPoints(List<DrawPoint> points) { this.points = points; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    @Override
    public String toString() {
        return "StrokeData{" +
                "id='" + id + '\'' +
                ", points=" + points.size() +
                ", username='" + username + '\'' +
                '}';
    }
}
