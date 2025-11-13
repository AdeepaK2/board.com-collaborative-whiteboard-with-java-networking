package org.example.server.modules;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.example.model.ChatMessage;

import java.net.Socket;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles chat message processing for rooms
 * 
 * Manages chat messages per room with history and broadcasting
 */
public class ChatHandler {
    private final Gson gson;
    // Store chat history per room (limited to last 100 messages)
    private final Map<String, List<ChatMessage>> chatHistory;
    private static final int MAX_HISTORY_PER_ROOM = 100;
    
    public ChatHandler() {
        this.gson = new Gson();
        this.chatHistory = new ConcurrentHashMap<>();
    }
    
    /**
     * Process chat message and return result
     */
    public ChatMessageResult handleChatMessage(Socket sender, String message,
                                               Map<Socket, String> clients,
                                               Map<Socket, String> clientRooms) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();
            
            switch (type) {
                case "chatMessage":
                    return handleChatMessage(sender, json, clients, clientRooms);
                    
                case "getChatHistory":
                    return handleGetChatHistory(sender, json, clientRooms);
                    
                default:
                    return ChatMessageResult.noAction();
            }
        } catch (Exception e) {
            System.err.println("Error handling chat message: " + e.getMessage());
            return ChatMessageResult.error("Invalid chat message format");
        }
    }
    
    /**
     * Handle incoming chat message
     */
    private ChatMessageResult handleChatMessage(Socket sender, JsonObject json,
                                                Map<Socket, String> clients,
                                                Map<Socket, String> clientRooms) {
        String username = clients.get(sender);
        String roomId = clientRooms.get(sender);
        
        if (username == null || roomId == null) {
            return ChatMessageResult.error("Not authenticated or not in a room");
        }
        
        String messageText = json.get("message").getAsString();
        
        // Create chat message
        ChatMessage chatMessage = new ChatMessage(
            ChatMessage.MessageType.CHAT,
            username,
            messageText,
            roomId
        );
        
        // Add to history
        addToHistory(roomId, chatMessage);
        
        // Create response JSON
        JsonObject response = new JsonObject();
        response.addProperty("type", "chatMessage");
        response.addProperty("username", username);
        response.addProperty("message", messageText);
        response.addProperty("timestamp", chatMessage.getTimestamp());
        
        System.out.println("💬 Chat [" + roomId + "] " + username + ": " + messageText);
        
        return ChatMessageResult.broadcastToRoom(roomId, response.toString());
    }
    
    /**
     * Handle chat history request
     */
    private ChatMessageResult handleGetChatHistory(Socket sender, JsonObject json,
                                                   Map<Socket, String> clientRooms) {
        String roomId = clientRooms.get(sender);
        
        if (roomId == null) {
            return ChatMessageResult.error("Not in a room");
        }
        
        List<ChatMessage> history = chatHistory.getOrDefault(roomId, new ArrayList<>());
        
        // Create response with chat history
        JsonObject response = new JsonObject();
        response.addProperty("type", "chatHistory");
        response.add("messages", gson.toJsonTree(history));
        
        return ChatMessageResult.sendToSender(response.toString());
    }
    
    /**
     * Add user joined notification to chat
     */
    public void addUserJoinedNotification(String roomId, String username) {
        ChatMessage notification = new ChatMessage(
            ChatMessage.MessageType.USER_JOINED,
            username,
            username + " joined the room",
            roomId
        );
        addToHistory(roomId, notification);
    }
    
    /**
     * Add user left notification to chat
     */
    public void addUserLeftNotification(String roomId, String username) {
        ChatMessage notification = new ChatMessage(
            ChatMessage.MessageType.USER_LEFT,
            username,
            username + " left the room",
            roomId
        );
        addToHistory(roomId, notification);
    }
    
    /**
     * Add message to chat history
     */
    private void addToHistory(String roomId, ChatMessage message) {
        chatHistory.computeIfAbsent(roomId, k -> new ArrayList<>());
        List<ChatMessage> history = chatHistory.get(roomId);
        
        history.add(message);
        
        // Limit history size
        if (history.size() > MAX_HISTORY_PER_ROOM) {
            history.remove(0);
        }
    }
    
    /**
     * Clear chat history for a room
     */
    public void clearRoomHistory(String roomId) {
        chatHistory.remove(roomId);
    }
    
    /**
     * Result class for chat message operations
     */
    public static class ChatMessageResult {
        public enum Action {
            SEND_TO_SENDER,
            BROADCAST_TO_ROOM,
            ERROR,
            NO_ACTION
        }
        
        public final Action action;
        public final String message;
        public final String roomId;
        
        private ChatMessageResult(Action action, String message, String roomId) {
            this.action = action;
            this.message = message;
            this.roomId = roomId;
        }
        
        public static ChatMessageResult sendToSender(String message) {
            return new ChatMessageResult(Action.SEND_TO_SENDER, message, null);
        }
        
        public static ChatMessageResult broadcastToRoom(String roomId, String message) {
            return new ChatMessageResult(Action.BROADCAST_TO_ROOM, message, roomId);
        }
        
        public static ChatMessageResult error(String errorMessage) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "error");
            json.addProperty("message", errorMessage);
            return new ChatMessageResult(Action.ERROR, json.toString(), null);
        }
        
        public static ChatMessageResult noAction() {
            return new ChatMessageResult(Action.NO_ACTION, null, null);
        }
    }
}
