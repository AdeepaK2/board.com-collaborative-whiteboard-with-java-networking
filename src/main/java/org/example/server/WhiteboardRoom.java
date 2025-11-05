package org.example.server;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages whiteboard rooms for multi-board collaboration
 * Each room has its own drawing history and set of connected clients
 */
public class WhiteboardRoom {
    private final String roomId;
    private final Map<Socket, String> clients;
    private final List<String> drawingHistory;
    private long lastActivity;
    
    public WhiteboardRoom(String roomId) {
        this.roomId = roomId;
        this.clients = new ConcurrentHashMap<>();
        this.drawingHistory = new ArrayList<>();
        this.lastActivity = System.currentTimeMillis();
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void addClient(Socket socket, String username) {
        clients.put(socket, username);
        updateActivity();
    }
    
    public void removeClient(Socket socket) {
        clients.remove(socket);
        updateActivity();
    }
    
    public String getUsername(Socket socket) {
        return clients.get(socket);
    }
    
    public Map<Socket, String> getClients() {
        return clients;
    }
    
    public int getClientCount() {
        return clients.size();
    }
    
    public void addToHistory(String message) {
        drawingHistory.add(message);
        updateActivity();
    }
    
    public List<String> getDrawingHistory() {
        return new ArrayList<>(drawingHistory);
    }
    
    public void clearHistory() {
        drawingHistory.clear();
        updateActivity();
    }
    
    public void replaceHistory(List<String> newHistory) {
        drawingHistory.clear();
        drawingHistory.addAll(newHistory);
        updateActivity();
    }
    
    public void removeFromHistory(String elementId) {
        drawingHistory.removeIf(msg -> msg.contains("\"id\":\"" + elementId + "\""));
        updateActivity();
    }
    
    public long getLastActivity() {
        return lastActivity;
    }
    
    private void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }
    
    public boolean isEmpty() {
        return clients.isEmpty();
    }
}
