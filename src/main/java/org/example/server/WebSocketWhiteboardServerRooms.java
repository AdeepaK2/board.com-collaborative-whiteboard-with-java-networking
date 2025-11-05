package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Enhanced WebSocket server with room support for board collaboration
 * Demonstrates multithreading with thread pool and room-based message routing
 */
public class WebSocketWhiteboardServerRooms {
    private static final int PORT = 8080;
    private static final String WEBSOCKET_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();
    private static final Map<String, WhiteboardRoom> rooms = new ConcurrentHashMap<>();
    private static final Map<Socket, String> socketToRoom = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    private static volatile boolean running = true;

    public static void main(String[] args) {
        System.out.println("Starting Whiteboard WebSocket Server (Room-based) on port " + PORT);
        System.out.println("React frontend should connect to: ws://localhost:" + PORT);
        System.out.println("To join a specific room: ws://localhost:" + PORT + "?room=ROOM_ID");
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());
                    
                    // Handle each client in a thread from the pool
                    threadPool.execute(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private static void handleClient(Socket clientSocket) {
        String roomId = null;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();

            // Read HTTP headers for WebSocket handshake
            String line;
            String key = null;
            String requestLine = null;
            
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (requestLine == null) {
                    requestLine = line;
                }
                if (line.startsWith("Sec-WebSocket-Key:")) {
                    key = line.substring(19).trim();
                }
            }

            // Extract room ID from request line if present
            if (requestLine != null && requestLine.contains("?room=")) {
                int roomStart = requestLine.indexOf("?room=") + 6;
                int roomEnd = requestLine.indexOf(" ", roomStart);
                if (roomEnd == -1) roomEnd = requestLine.length();
                roomId = requestLine.substring(roomStart, roomEnd);
                System.out.println("Client requested room: " + roomId);
            }

            if (key != null) {
                // Send WebSocket handshake response
                String accept = generateWebSocketAccept(key);
                
                String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                                "Upgrade: websocket\r\n" +
                                "Connection: Upgrade\r\n" +
                                "Sec-WebSocket-Accept: " + accept + "\r\n" +
                                "Access-Control-Allow-Origin: *\r\n\r\n";
                
                out.write(response.getBytes());
                out.flush();
                
                System.out.println("WebSocket handshake completed");

                // Handle WebSocket messages
                handleWebSocketMessages(clientSocket, roomId);
            }
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            cleanup(clientSocket);
        }
    }
    
    private static void cleanup(Socket clientSocket) {
        String roomId = socketToRoom.remove(clientSocket);
        if (roomId != null) {
            WhiteboardRoom room = rooms.get(roomId);
            if (room != null) {
                String username = room.getUsername(clientSocket);
                room.removeClient(clientSocket);
                
                System.out.println("Client disconnected from room " + roomId + ": " + username);
                
                // Notify other clients in the room
                JsonObject leaveMessage = new JsonObject();
                leaveMessage.addProperty("type", "userLeft");
                leaveMessage.addProperty("username", username);
                broadcastToRoom(roomId, leaveMessage.toString(), clientSocket);
                
                // Clean up empty rooms
                if (room.isEmpty()) {
                    rooms.remove(roomId);
                    System.out.println("Room " + roomId + " is now empty and removed");
                }
            }
        }
        
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }

    private static String generateWebSocketAccept(String key) throws NoSuchAlgorithmException {
        String combined = key + WEBSOCKET_MAGIC_STRING;
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(combined.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static void handleWebSocketMessages(Socket clientSocket, String initialRoomId) {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            
            while (!clientSocket.isClosed() && running) {
                byte[] buffer = new byte[8192];
                int bytesRead = inputStream.read(buffer);
                
                if (bytesRead == -1) {
                    break; // Client disconnected
                }
                
                if (bytesRead >= 2) {
                    String message = decodeWebSocketFrame(buffer, bytesRead);
                    if (message != null && !message.isEmpty()) {
                        handleMessage(clientSocket, message, initialRoomId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in WebSocket message handling: " + e.getMessage());
        }
    }

    private static String decodeWebSocketFrame(byte[] buffer, int length) {
        try {
            if (length < 2) return null;
            
            boolean fin = (buffer[0] & 0x80) != 0;
            int opcode = buffer[0] & 0x0F;
            boolean masked = (buffer[1] & 0x80) != 0;
            int payloadLength = buffer[1] & 0x7F;
            
            if (opcode != 0x1 || !fin) { // Only handle text frames
                return null;
            }
            
            int offset = 2;
            
            // Handle extended payload length
            if (payloadLength == 126) {
                if (length < 4) return null;
                payloadLength = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                offset = 4;
            } else if (payloadLength == 127) {
                offset = 10; // Skip 8 bytes for 64-bit length
            }
            
            // Handle masking key
            byte[] maskingKey = new byte[4];
            if (masked) {
                if (length < offset + 4) return null;
                System.arraycopy(buffer, offset, maskingKey, 0, 4);
                offset += 4;
            }
            
            // Extract payload
            if (length < offset + payloadLength) {
                payloadLength = length - offset; // Adjust if incomplete frame
            }
            
            byte[] payload = new byte[payloadLength];
            System.arraycopy(buffer, offset, payload, 0, payloadLength);
            
            // Unmask payload
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskingKey[i % 4];
                }
            }
            
            return new String(payload, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Error decoding WebSocket frame: " + e.getMessage());
            return null;
        }
    }

    private static void handleMessage(Socket sender, String message, String initialRoomId) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();
            
            switch (type) {
                case "join":
                    handleJoinMessage(sender, json, initialRoomId);
                    break;
                case "draw":
                case "shape":
                    handleDrawMessage(sender, message);
                    break;
                case "update":
                    handleUpdateMessage(sender, message);
                    break;
                case "delete":
                    handleDeleteMessage(sender, message);
                    break;
                case "cursor":
                    handleCursorMessage(sender, message);
                    break;
                case "clear":
                    handleClearMessage(sender);
                    break;
                case "loadBoard":
                    handleLoadBoardMessage(sender, json);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
            }
        } catch (JsonSyntaxException e) {
            System.err.println("Invalid JSON message: " + message);
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleJoinMessage(Socket sender, JsonObject json, String initialRoomId) {
        String username = json.get("username").getAsString();
        String roomId = json.has("roomId") ? json.get("roomId").getAsString() : 
                        (initialRoomId != null ? initialRoomId : "default");
        
        // Get or create room
        WhiteboardRoom room = rooms.computeIfAbsent(roomId, WhiteboardRoom::new);
        
        // Add client to room
        room.addClient(sender, username);
        socketToRoom.put(sender, roomId);
        
        System.out.println("User joined room " + roomId + ": " + username + 
                          " (Room users: " + room.getClientCount() + ")");
        
        // Send room info to the client
        JsonObject roomInfo = new JsonObject();
        roomInfo.addProperty("type", "roomInfo");
        roomInfo.addProperty("roomId", roomId);
        roomInfo.addProperty("userCount", room.getClientCount());
        sendWebSocketMessage(sender, roomInfo.toString());
        
        // Send drawing history to new user
        for (String historyMessage : room.getDrawingHistory()) {
            sendWebSocketMessage(sender, historyMessage);
        }
        
        // Broadcast user joined to others in the room
        JsonObject response = new JsonObject();
        response.addProperty("type", "userJoined");
        response.addProperty("username", username);
        broadcastToRoom(roomId, response.toString(), sender);
    }
    
    private static void handleLoadBoardMessage(Socket sender, JsonObject json) {
        // When a board is loaded, replace the room's history
        if (json.has("strokes") && json.has("shapes")) {
            String roomId = socketToRoom.get(sender);
            if (roomId != null) {
                WhiteboardRoom room = rooms.get(roomId);
                if (room != null) {
                    room.clearHistory();
                    System.out.println("Board loaded into room: " + roomId);
                    
                    // History will be populated by the client sending draw/shape messages
                }
            }
        }
    }

    private static void handleDrawMessage(Socket sender, String message) {
        String roomId = socketToRoom.get(sender);
        if (roomId != null) {
            WhiteboardRoom room = rooms.get(roomId);
            if (room != null) {
                room.addToHistory(message);
                broadcastToRoom(roomId, message, sender);
                System.out.println("Broadcasted drawing to room " + roomId);
            }
        }
    }

    private static void handleCursorMessage(Socket sender, String message) {
        String roomId = socketToRoom.get(sender);
        if (roomId != null) {
            broadcastToRoom(roomId, message, sender);
        }
    }

    private static void handleUpdateMessage(Socket sender, String message) {
        String roomId = socketToRoom.get(sender);
        if (roomId != null) {
            WhiteboardRoom room = rooms.get(roomId);
            if (room != null) {
                try {
                    JsonObject json = gson.fromJson(message, JsonObject.class);
                    String elementId = json.get("elementId").getAsString();
                    
                    // Remove old version from history
                    room.removeFromHistory(elementId);
                    
                    // Add updated version
                    room.addToHistory(message);
                    
                    broadcastToRoom(roomId, message, sender);
                    System.out.println("Broadcasted update to room " + roomId);
                } catch (Exception e) {
                    System.err.println("Error handling update message: " + e.getMessage());
                }
            }
        }
    }

    private static void handleDeleteMessage(Socket sender, String message) {
        String roomId = socketToRoom.get(sender);
        if (roomId != null) {
            WhiteboardRoom room = rooms.get(roomId);
            if (room != null) {
                try {
                    JsonObject json = gson.fromJson(message, JsonObject.class);
                    String elementId = json.get("elementId").getAsString();
                    
                    room.removeFromHistory(elementId);
                    broadcastToRoom(roomId, message, sender);
                    System.out.println("Broadcasted delete to room " + roomId);
                } catch (Exception e) {
                    System.err.println("Error handling delete message: " + e.getMessage());
                }
            }
        }
    }

    private static void handleClearMessage(Socket sender) {
        String roomId = socketToRoom.get(sender);
        if (roomId != null) {
            WhiteboardRoom room = rooms.get(roomId);
            if (room != null) {
                room.clearHistory();
                
                JsonObject clearMessage = new JsonObject();
                clearMessage.addProperty("type", "clear");
                clearMessage.addProperty("username", room.getUsername(sender));
                broadcastToRoom(roomId, clearMessage.toString(), sender);
                System.out.println("Canvas cleared in room " + roomId);
            }
        }
    }

    private static void broadcastToRoom(String roomId, String message, Socket sender) {
        WhiteboardRoom room = rooms.get(roomId);
        if (room != null) {
            for (Socket client : room.getClients().keySet()) {
                if (client != sender && !client.isClosed()) {
                    sendWebSocketMessage(client, message);
                }
            }
        }
    }

    private static void sendWebSocketMessage(Socket client, String message) {
        try {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            OutputStream out = client.getOutputStream();
            
            // Write WebSocket frame header
            out.write(0x81); // FIN + text frame
            
            // Write payload length
            if (payload.length < 126) {
                out.write(payload.length);
            } else if (payload.length < 65536) {
                out.write(126);
                out.write((payload.length >> 8) & 0xFF);
                out.write(payload.length & 0xFF);
            } else {
                out.write(127);
                for (int i = 0; i < 8; i++) {
                    out.write(0);
                }
            }
            
            // Write payload
            out.write(payload);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error sending message to client: " + e.getMessage());
        }
    }

    public static void stopServer() {
        running = false;
        threadPool.shutdown();
    }
}
