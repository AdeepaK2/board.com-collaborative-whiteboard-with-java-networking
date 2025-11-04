package org.example.server.modules;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.example.model.Room;

import java.net.Socket;
import java.util.*;

/**
 * Handles application-level message processing
 * 
 * LESSON 9: Application Protocol - defines custom JSON-based protocol
 * on top of WebSocket
 */
public class MessageHandler {
    private final RoomManager roomManager;
    private final Gson gson;
    
    public MessageHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
        this.gson = new Gson();
    }
    
    /**
     * Process incoming message and return response
     */
    public MessageResult handleMessage(Socket sender, String message, 
                                       Map<Socket, String> clients, 
                                       Map<Socket, String> clientRooms) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();
            
            switch (type) {
                case "getRooms":
                    return handleGetRooms();
                    
                case "createRoom":
                    return handleCreateRoom(sender, json, clients, clientRooms);
                    
                case "joinRoom":
                    return handleJoinRoom(sender, json, clients, clientRooms);
                    
                case "draw":
                    return handleDraw(sender, message, clientRooms);
                    
                case "clear":
                    return handleClear(sender, clients, clientRooms);
                    
                case "cursor":
                    return handleCursor(sender, message, clientRooms);
                    
                default:
                    System.out.println("Unknown message type: " + type);
                    return MessageResult.noAction();
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            return MessageResult.error("Invalid message format");
        }
    }
    
    private MessageResult handleGetRooms() {
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomList");
        response.add("rooms", getRoomsAsJson());
        
        return MessageResult.sendToSender(response.toString());
    }
    
    private MessageResult handleCreateRoom(Socket sender, JsonObject json,
                                          Map<Socket, String> clients,
                                          Map<Socket, String> clientRooms) {
        String username = json.get("username").getAsString();
        String roomName = json.get("roomName").getAsString();
        String roomId = UUID.randomUUID().toString();
        
        Room room = roomManager.createRoom(roomId, roomName, username);
        room.addParticipant(username);
        
        clients.put(sender, username);
        clientRooms.put(sender, roomId);
        
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomCreated");
        response.addProperty("roomId", roomId);
        response.addProperty("roomName", roomName);
        
        return MessageResult.sendToSenderAndBroadcastRoomList(response.toString());
    }
    
    private MessageResult handleJoinRoom(Socket sender, JsonObject json,
                                        Map<Socket, String> clients,
                                        Map<Socket, String> clientRooms) {
        String username = json.get("username").getAsString();
        String roomId = json.get("roomId").getAsString();
        
        Room room = roomManager.getRoom(roomId);
        
        if (room == null) {
            return MessageResult.error("Room not found");
        }
        
        if (room.isFull()) {
            return MessageResult.error("Room is full");
        }
        
        room.addParticipant(username);
        clients.put(sender, username);
        clientRooms.put(sender, roomId);
        
        System.out.println("👤 " + username + " joined room: " + room.getRoomName());
        
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomJoined");
        response.addProperty("roomId", roomId);
        response.addProperty("roomName", room.getRoomName());
        
        // Prepare join notification for others
        JsonObject joinNotification = new JsonObject();
        joinNotification.addProperty("type", "userJoined");
        joinNotification.addProperty("username", username);
        
        return MessageResult.joinRoom(response.toString(), room.getDrawingHistory(), 
                                     joinNotification.toString(), roomId);
    }
    
    private MessageResult handleDraw(Socket sender, String message, 
                                    Map<Socket, String> clientRooms) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) return MessageResult.noAction();
        
        Room room = roomManager.getRoom(roomId);
        if (room == null) return MessageResult.noAction();
        
        room.addToDrawingHistory(message);
        return MessageResult.broadcastToRoom(message, roomId);
    }
    
    private MessageResult handleClear(Socket sender, Map<Socket, String> clients,
                                     Map<Socket, String> clientRooms) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) return MessageResult.noAction();
        
        Room room = roomManager.getRoom(roomId);
        if (room == null) return MessageResult.noAction();
        
        room.clearDrawingHistory();
        
        JsonObject clearMsg = new JsonObject();
        clearMsg.addProperty("type", "clear");
        clearMsg.addProperty("username", clients.get(sender));
        
        System.out.println("🗑️  Canvas cleared in room: " + room.getRoomName());
        
        return MessageResult.broadcastToRoom(clearMsg.toString(), roomId);
    }
    
    private MessageResult handleCursor(Socket sender, String message,
                                      Map<Socket, String> clientRooms) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) return MessageResult.noAction();
        
        return MessageResult.broadcastToRoom(message, roomId);
    }
    
    /**
     * Build room list as JSON
     */
    public JsonArray getRoomsAsJson() {
        JsonArray roomArray = new JsonArray();
        for (Room room : roomManager.getAllRooms()) {
            JsonObject roomObj = new JsonObject();
            roomObj.addProperty("roomId", room.getRoomId());
            roomObj.addProperty("roomName", room.getRoomName());
            roomObj.addProperty("creator", room.getCreatorUsername());
            roomObj.addProperty("participants", room.getParticipantCount());
            roomObj.addProperty("maxParticipants", room.getMaxParticipants());
            roomArray.add(roomObj);
        }
        return roomArray;
    }
    
    /**
     * Result of message handling operation
     */
    public static class MessageResult {
        public enum Action {
            NO_ACTION,
            SEND_TO_SENDER,
            BROADCAST_TO_ROOM,
            JOIN_ROOM,
            BROADCAST_ROOM_LIST,
            ERROR
        }
        
        public final Action action;
        public final String message;
        public final String roomId;
        public final List<String> history;
        public final String broadcastMessage;
        
        private MessageResult(Action action, String message, String roomId, 
                            List<String> history, String broadcastMessage) {
            this.action = action;
            this.message = message;
            this.roomId = roomId;
            this.history = history;
            this.broadcastMessage = broadcastMessage;
        }
        
        public static MessageResult noAction() {
            return new MessageResult(Action.NO_ACTION, null, null, null, null);
        }
        
        public static MessageResult sendToSender(String message) {
            return new MessageResult(Action.SEND_TO_SENDER, message, null, null, null);
        }
        
        public static MessageResult broadcastToRoom(String message, String roomId) {
            return new MessageResult(Action.BROADCAST_TO_ROOM, message, roomId, null, null);
        }
        
        public static MessageResult joinRoom(String message, List<String> history, 
                                            String broadcastMsg, String roomId) {
            return new MessageResult(Action.JOIN_ROOM, message, roomId, history, broadcastMsg);
        }
        
        public static MessageResult sendToSenderAndBroadcastRoomList(String message) {
            return new MessageResult(Action.BROADCAST_ROOM_LIST, message, null, null, null);
        }
        
        public static MessageResult error(String errorMessage) {
            JsonObject error = new JsonObject();
            error.addProperty("type", "error");
            error.addProperty("message", errorMessage);
            return new MessageResult(Action.ERROR, error.toString(), null, null, null);
        }
    }
}
