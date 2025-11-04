package org.example.server.modules;

import org.example.model.Room;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages whiteboard rooms - creation, joining, participant tracking
 * 
 * LESSON 6: Thread-safe using ConcurrentHashMap
 * Multiple threads can access this class safely
 */
public class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    
    /**
     * Create a new room
     */
    public Room createRoom(String roomId, String roomName, String creatorUsername) {
        Room room = new Room(roomId, roomName, creatorUsername);
        rooms.put(roomId, room);
        System.out.println("🎨 Created room: " + roomName + " [" + roomId + "]");
        return room;
    }
    
    /**
     * Get a room by ID
     */
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }
    
    /**
     * Get all rooms
     */
    public Collection<Room> getAllRooms() {
        return rooms.values();
    }
    
    /**
     * Remove a room
     */
    public void removeRoom(String roomId) {
        Room removed = rooms.remove(roomId);
        if (removed != null) {
            System.out.println("🗑️  Removed room: " + removed.getRoomName());
        }
    }
    
    /**
     * Check if room exists
     */
    public boolean roomExists(String roomId) {
        return rooms.containsKey(roomId);
    }
    
    /**
     * Get total number of rooms
     */
    public int getRoomCount() {
        return rooms.size();
    }
    
    /**
     * Clean up empty rooms (except if it's the only room)
     */
    public void cleanupEmptyRooms() {
        if (rooms.size() <= 1) {
            return;
        }
        
        List<String> emptyRoomIds = new ArrayList<>();
        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            if (entry.getValue().isEmpty()) {
                emptyRoomIds.add(entry.getKey());
            }
        }
        
        for (String roomId : emptyRoomIds) {
            removeRoom(roomId);
        }
    }
}
