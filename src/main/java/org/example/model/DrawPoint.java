package org.example.model;

import java.io.Serializable;

/**
 * Represents a single point in a drawing stroke
 */
public class DrawPoint implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private double x;
    private double y;
    private String color;
    private int size;
    
    public DrawPoint() {}
    
    public DrawPoint(double x, double y, String color, int size) {
        this.x = x;
        this.y = y;
        this.color = color;
        this.size = size;
    }
    
    // Getters and Setters
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    
    @Override
    public String toString() {
        return "DrawPoint{" +
                "x=" + x +
                ", y=" + y +
                ", color='" + color + '\'' +
                ", size=" + size +
                '}';
    }
}
