package org.example.model;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a whiteboard room where multiple users can collaborate
 */
public class Room implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String roomId;
    private String roomName;
    private String creatorUsername;
    private long createdTime;
    private Set<String> participants;
    private List<String> drawingHistory;
    private int maxParticipants;
    private boolean isPublic;
    
    public Room() {
        this.createdTime = System.currentTimeMillis();
        this.participants = ConcurrentHashMap.newKeySet();
        this.drawingHistory = Collections.synchronizedList(new ArrayList<>());
        this.maxParticipants = 50;
        this.isPublic = true;
    }
    
    public Room(String roomId, String roomName, String creatorUsername) {
        this();
        this.roomId = roomId;
        this.roomName = roomName;
        this.creatorUsername = creatorUsername;
    }
    
    public void addParticipant(String username) {
        participants.add(username);
    }
    
    public void removeParticipant(String username) {
        participants.remove(username);
    }
    
    public boolean isFull() {
        return participants.size() >= maxParticipants;
    }
    
    public boolean isEmpty() {
        return participants.isEmpty();
    }
    
    // Getters and Setters
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    
    public String getCreatorUsername() { return creatorUsername; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }
    
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
    
    public Set<String> getParticipants() { return new HashSet<>(participants); }
    public int getParticipantCount() { return participants.size(); }
    
    public List<String> getDrawingHistory() { return new ArrayList<>(drawingHistory); }
    public void addToDrawingHistory(String message) { drawingHistory.add(message); }
    public void clearDrawingHistory() { drawingHistory.clear(); }
    
    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }
    
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Room room = (Room) obj;
        return roomId != null ? roomId.equals(room.roomId) : room.roomId == null;
    }
    
    @Override
    public int hashCode() {
        return roomId != null ? roomId.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return "Room{" +
                "roomId='" + roomId + '\'' +
                ", roomName='" + roomName + '\'' +
                ", creatorUsername='" + creatorUsername + '\'' +
                ", participantCount=" + participants.size() +
                ", createdTime=" + createdTime +
                '}';
    }
}
