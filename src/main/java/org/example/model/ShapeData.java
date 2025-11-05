package org.example.model;

import java.io.Serializable;

/**
 * Represents a geometric shape (rectangle, circle, line, triangle)
 * for the collaborative whiteboard
 */
public class ShapeData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String type; // "rectangle", "circle", "line", "triangle"
    private double x;
    private double y;
    private Double width;
    private Double height;
    private Double radius;
    private Double endX; // For line
    private Double endY; // For line
    private String color;
    private int size;
    private String fillColor;
    private String username;
    private long timestamp;

    public ShapeData() {}

    public ShapeData(String id, String type, double x, double y, String color, int size, String username) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.color = color;
        this.size = size;
        this.username = username;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }

    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }

    public Double getRadius() { return radius; }
    public void setRadius(Double radius) { this.radius = radius; }

    public Double getEndX() { return endX; }
    public void setEndX(Double endX) { this.endX = endX; }

    public Double getEndY() { return endY; }
    public void setEndY(Double endY) { this.endY = endY; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getFillColor() { return fillColor; }
    public void setFillColor(String fillColor) { this.fillColor = fillColor; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "ShapeData{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", username='" + username + '\'' +
                '}';
    }
}
