package org.example.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a whiteboard with all its drawing elements
 * Used for saving/loading boards to/from storage
 */
public class Board implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String name;
    private String createdBy;
    private long createdAt;
    private long lastModified;
    private List<StrokeData> strokes;
    private List<ShapeData> shapes;
    
    public Board() {
        this.strokes = new ArrayList<>();
        this.shapes = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.lastModified = this.createdAt;
    }
    
    public Board(String id, String name, String createdBy) {
        this();
        this.id = id;
        this.name = name;
        this.createdBy = createdBy;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    
    public List<StrokeData> getStrokes() { return strokes; }
    public void setStrokes(List<StrokeData> strokes) { this.strokes = strokes; }
    
    public List<ShapeData> getShapes() { return shapes; }
    public void setShapes(List<ShapeData> shapes) { this.shapes = shapes; }
    
    @Override
    public String toString() {
        return "Board{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", createdBy='" + createdBy + '\'' +
                ", strokes=" + strokes.size() +
                ", shapes=" + shapes.size() +
                '}';
    }
}
