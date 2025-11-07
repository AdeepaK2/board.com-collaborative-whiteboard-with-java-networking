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
                case "setUsername":
                    return handleSetUsername(sender, json, clients);
                    
                case "getRooms":
                    return handleGetRooms();
                    
                case "getActiveUsers":
                    return handleGetActiveUsers(clients);
                    
                case "createRoom":
                    return handleCreateRoom(sender, json, clients, clientRooms);
                    
                case "joinRoom":
                    return handleJoinRoom(sender, json, clients, clientRooms);
                    
                case "draw":
                    return handleDraw(sender, message, clientRooms);
                    
                case "addShape":
                    return handleAddShape(sender, message, clientRooms);
                    
                case "updateShape":
                    return handleUpdateShape(sender, message, clientRooms);
                    
                case "deleteShape":
                    return handleDeleteShape(sender, message, clientRooms);
                    
                case "clear":
                    return handleClear(sender, clients, clientRooms);
                    
                case "cursor":
                    return handleCursor(sender, message, clientRooms);
                    
                case "leaveRoom":
                    return handleLeaveRoom(sender, clients, clientRooms);
                    
                default:
                    System.out.println("Unknown message type: " + type);
                    return MessageResult.noAction();
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            return MessageResult.error("Invalid message format");
        }
    }
    
    private MessageResult handleSetUsername(Socket sender, JsonObject json,
                                           Map<Socket, String> clients) {
        String username = json.get("username").getAsString();
        clients.put(sender, username);
        
        System.out.println("✓ Username set: " + username);
        
        // Send back room list after username is set (including rooms user is invited to)
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomList");
        response.add("rooms", getRoomsAsJsonForUser(username));
        
        return MessageResult.sendToSender(response.toString());
    }
    
    private MessageResult handleGetRooms() {
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomList");
        response.add("rooms", getRoomsAsJson());
        
        return MessageResult.sendToSender(response.toString());
    }
    
    private MessageResult handleGetActiveUsers(Map<Socket, String> clients) {
        JsonObject response = new JsonObject();
        response.addProperty("type", "activeUsers");
        
        JsonArray usersArray = new JsonArray();
        for (String username : clients.values()) {
            if (username != null && !username.isEmpty()) {
                usersArray.add(username);
            }
        }
        
        response.add("users", usersArray);
        
        return MessageResult.sendToSender(response.toString());
    }
    
    /**
     * Build room list as JSON for a specific user
     * Returns public rooms + private rooms the user is invited to
     */
    public JsonArray getRoomsAsJsonForUser(String username) {
        JsonArray roomArray = new JsonArray();
        for (Room room : roomManager.getAllRooms()) {
            // Include public rooms OR private rooms where user is invited or creator
            if (room.isPublic() || room.isUserInvited(username) || username.equals(room.getCreatorUsername())) {
                JsonObject roomObj = new JsonObject();
                roomObj.addProperty("roomId", room.getRoomId());
                roomObj.addProperty("roomName", room.getRoomName());
                roomObj.addProperty("creator", room.getCreatorUsername());
                roomObj.addProperty("participants", room.getParticipantCount());
                roomObj.addProperty("maxParticipants", room.getMaxParticipants());
                roomObj.addProperty("isPublic", room.isPublic());
                roomObj.addProperty("hasPassword", room.hasPassword());
                roomArray.add(roomObj);
            }
        }
        return roomArray;
    }
    
    private MessageResult handleCreateRoom(Socket sender, JsonObject json,
                                          Map<Socket, String> clients,
                                          Map<Socket, String> clientRooms) {
        String username = clients.get(sender);
        if (username == null) {
            return MessageResult.error("Username not set");
        }
        
        String roomName = json.get("roomName").getAsString();
        String roomId = UUID.randomUUID().toString();

        // Check for public/private settings
        boolean isPublic = json.has("isPublic") ? json.get("isPublic").getAsBoolean() : true;
        String password = null;
        if (json.has("password") && !json.get("password").isJsonNull()) {
            password = json.get("password").getAsString();
        }

        // Get invited users for private rooms
        List<String> invitedUsers = new ArrayList<>();
        if (json.has("invitedUsers") && json.get("invitedUsers").isJsonArray()) {
            JsonArray invitedArray = json.get("invitedUsers").getAsJsonArray();
            for (int i = 0; i < invitedArray.size(); i++) {
                invitedUsers.add(invitedArray.get(i).getAsString());
            }
        }

        Room room = roomManager.createRoom(roomId, roomName, username, isPublic, password, invitedUsers);
        room.addParticipant(username);

        clients.put(sender, username);
        clientRooms.put(sender, roomId);

        JsonObject response = new JsonObject();
        response.addProperty("type", "roomCreated");
        response.addProperty("roomId", roomId);
        response.addProperty("roomName", roomName);
        response.addProperty("isPublic", isPublic);

        // Create notification for public or private room
        if (isPublic) {
            // Broadcast notification to ALL clients about new public room
            JsonObject publicNotification = new JsonObject();
            publicNotification.addProperty("type", "newPublicRoom");
            publicNotification.addProperty("roomId", roomId);
            publicNotification.addProperty("roomName", roomName);
            publicNotification.addProperty("creator", username);

            return MessageResult.broadcastNewPublicRoom(response.toString(), publicNotification.toString());
        } else {
            // Multicast notification to invited users only
            JsonObject privateNotification = new JsonObject();
            privateNotification.addProperty("type", "newPrivateRoomInvite");
            privateNotification.addProperty("roomId", roomId);
            privateNotification.addProperty("roomName", roomName);
            privateNotification.addProperty("creator", username);
            privateNotification.addProperty("hasPassword", room.hasPassword());

            return MessageResult.multicastToInvitedUsers(response.toString(),
                                                         privateNotification.toString(),
                                                         invitedUsers);
        }
    }
    
    private MessageResult handleJoinRoom(Socket sender, JsonObject json,
                                        Map<Socket, String> clients,
                                        Map<Socket, String> clientRooms) {
        String username = clients.get(sender);
        if (username == null) {
            return MessageResult.error("Username not set");
        }
        
        String roomId = json.get("roomId").getAsString();
        String password = null;
        if (json.has("password") && !json.get("password").isJsonNull()) {
            password = json.get("password").getAsString();
        }

        Room room = roomManager.getRoom(roomId);

        if (room == null) {
            return MessageResult.error("Room not found");
        }

        // Check if room is private and user is invited
        if (!room.isPublic()) {
            if (!room.isUserInvited(username) && !username.equals(room.getCreatorUsername())) {
                return MessageResult.error("You are not invited to this private room");
            }
        }

        // Validate password if room has one
        if (room.hasPassword()) {
            if (!room.validatePassword(password)) {
                return MessageResult.error("Incorrect password");
            }
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
    
    private MessageResult handleLeaveRoom(Socket sender, Map<Socket, String> clients,
                                         Map<Socket, String> clientRooms) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) return MessageResult.noAction();
        
        String username = clients.get(sender);
        Room room = roomManager.getRoom(roomId);
        
        if (room != null && username != null) {
            room.removeParticipant(username);
            clientRooms.remove(sender);
            
            System.out.println("👋 " + username + " left room: " + room.getRoomName());
            
            JsonObject leaveNotification = new JsonObject();
            leaveNotification.addProperty("type", "userLeft");
            leaveNotification.addProperty("username", username);
            leaveNotification.addProperty("participants", room.getParticipantCount());
            
            return MessageResult.broadcastToRoom(leaveNotification.toString(), roomId);
        }
        
        return MessageResult.noAction();
    }
    
    /**
     * Build room list as JSON (only returns public rooms)
     */
    public JsonArray getRoomsAsJson() {
        JsonArray roomArray = new JsonArray();
        for (Room room : roomManager.getAllRooms()) {
            // Only include public rooms in general room list
            if (room.isPublic()) {
                JsonObject roomObj = new JsonObject();
                roomObj.addProperty("roomId", room.getRoomId());
                roomObj.addProperty("roomName", room.getRoomName());
                roomObj.addProperty("creator", room.getCreatorUsername());
                roomObj.addProperty("participants", room.getParticipantCount());
                roomObj.addProperty("maxParticipants", room.getMaxParticipants());
                roomObj.addProperty("isPublic", room.isPublic());
                roomObj.addProperty("hasPassword", room.hasPassword());
                roomArray.add(roomObj);
            }
        }
        return roomArray;
    }
    
    /**
     * Handle adding a shape
     */
    private MessageResult handleAddShape(Socket sender, String message,
                                        Map<Socket, String> clientRooms) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) {
            return MessageResult.error("Not in a room");
        }
        
        Room room = roomManager.getRoom(roomId);
        if (room != null) {
            // Store shape in room history
            room.addToDrawingHistory(message);
            
            // Broadcast to all users in the room
            return MessageResult.broadcastToRoom(message, roomId);
        }
        
        return MessageResult.noAction();
    }
    
    /**
     * Handle updating a shape (move, resize, fill)
     */
    private MessageResult handleUpdateShape(Socket sender, String message,
                                           Map<Socket, String> clientRooms) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) {
            return MessageResult.error("Not in a room");
        }
        
        Room room = roomManager.getRoom(roomId);
        if (room != null) {
            // Broadcast shape update to all users
            return MessageResult.broadcastToRoom(message, roomId);
        }
        
        return MessageResult.noAction();
    }
    
    /**
     * Handle deleting a shape
     */
    private MessageResult handleDeleteShape(Socket sender, String message,
                                           Map<Socket, String> clientRooms) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) {
            return MessageResult.error("Not in a room");
        }
        
        Room room = roomManager.getRoom(roomId);
        if (room != null) {
            // Broadcast shape deletion to all users
            return MessageResult.broadcastToRoom(message, roomId);
        }
        
        return MessageResult.noAction();
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
            BROADCAST_NEW_PUBLIC_ROOM,
            MULTICAST_TO_INVITED_USERS,
            ERROR
        }

        public final Action action;
        public final String message;
        public final String roomId;
        public final List<String> history;
        public final String broadcastMessage;
        public final List<String> invitedUsers;
        
        private MessageResult(Action action, String message, String roomId,
                            List<String> history, String broadcastMessage, List<String> invitedUsers) {
            this.action = action;
            this.message = message;
            this.roomId = roomId;
            this.history = history;
            this.broadcastMessage = broadcastMessage;
            this.invitedUsers = invitedUsers;
        }
        
        public static MessageResult noAction() {
            return new MessageResult(Action.NO_ACTION, null, null, null, null, null);
        }

        public static MessageResult sendToSender(String message) {
            return new MessageResult(Action.SEND_TO_SENDER, message, null, null, null, null);
        }

        public static MessageResult broadcastToRoom(String message, String roomId) {
            return new MessageResult(Action.BROADCAST_TO_ROOM, message, roomId, null, null, null);
        }

        public static MessageResult joinRoom(String message, List<String> history,
                                            String broadcastMsg, String roomId) {
            return new MessageResult(Action.JOIN_ROOM, message, roomId, history, broadcastMsg, null);
        }

        public static MessageResult sendToSenderAndBroadcastRoomList(String message) {
            return new MessageResult(Action.BROADCAST_ROOM_LIST, message, null, null, null, null);
        }

        public static MessageResult broadcastNewPublicRoom(String message, String notification) {
            return new MessageResult(Action.BROADCAST_NEW_PUBLIC_ROOM, message, null, null, notification, null);
        }

        public static MessageResult multicastToInvitedUsers(String message, String notification, List<String> invitedUsers) {
            return new MessageResult(Action.MULTICAST_TO_INVITED_USERS, message, null, null, notification, invitedUsers);
        }

        public static MessageResult error(String errorMessage) {
            JsonObject error = new JsonObject();
            error.addProperty("type", "error");
            error.addProperty("message", errorMessage);
            return new MessageResult(Action.ERROR, error.toString(), null, null, null, null);
        }
    }
}
