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
     * Create a new room with public/private settings and optional password
     * @param roomId Unique room identifier
     * @param roomName Display name of the room
     * @param creatorUsername Username of the creator
     * @param isPublic True for public room, false for private
     * @param password Optional password (can be null)
     * @param invitedUsers List of usernames invited to private room (can be null)
     */
    public Room createRoom(String roomId, String roomName, String creatorUsername,
                          boolean isPublic, String password, List<String> invitedUsers) {
        Room room = new Room(roomId, roomName, creatorUsername);
        room.setPublic(isPublic);

        if (password != null && !password.isEmpty()) {
            room.setPassword(password);
        }

        if (invitedUsers != null && !isPublic) {
            for (String username : invitedUsers) {
                room.addInvitedUser(username);
            }
        }

        rooms.put(roomId, room);

        String roomType = isPublic ? "public" : "private";
        String passwordInfo = room.hasPassword() ? " (password protected)" : "";
        System.out.println("🎨 Created " + roomType + " room: " + roomName + " [" + roomId + "]" + passwordInfo);

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
