package org.example.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonSyntaxException;
import org.example.model.Room;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-room WebSocket server for collaborative whiteboard
 * Users can create and join different whiteboard rooms
 */
public class MultiRoomWebSocketServer {
    private static final int PORT = 8080;
    private static final String WEBSOCKET_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    // Map of socket to username
    private static final Map<Socket, String> clients = new ConcurrentHashMap<>();
    // Map of socket to room ID
    private static final Map<Socket, String> clientRooms = new ConcurrentHashMap<>();
    // Map of room ID to Room object
    private static final Map<String, Room> rooms = new ConcurrentHashMap<>();
    
    private static final Gson gson = new Gson();
    private static volatile boolean running = true;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("🎨 Multi-Room Whiteboard Server");
        System.out.println("=================================================");
        displayNetworkAddresses();
        System.out.println("=================================================");
        System.out.println("Waiting for connections...");
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New connection from: " + clientSocket.getInetAddress());
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();

            // Read HTTP headers for WebSocket handshake
            String line;
            String key = null;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Sec-WebSocket-Key:")) {
                    key = line.substring(19).trim();
                }
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
                
                System.out.println("✅ WebSocket handshake completed");

                // Handle WebSocket messages
                handleWebSocketMessages(clientSocket);
            }
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            cleanupClient(clientSocket);
        }
    }

    private static void cleanupClient(Socket clientSocket) {
        String username = clients.remove(clientSocket);
        String roomId = clientRooms.remove(clientSocket);
        
        if (username != null && roomId != null) {
            Room room = rooms.get(roomId);
            if (room != null) {
                room.removeParticipant(username);
                System.out.println("👋 " + username + " left room: " + room.getRoomName());
                
                // Notify other users in the room
                JsonObject leaveMessage = new JsonObject();
                leaveMessage.addProperty("type", "userLeft");
                leaveMessage.addProperty("username", username);
                leaveMessage.addProperty("roomId", roomId);
                broadcastToRoom(roomId, leaveMessage.toString(), clientSocket);
                
                // Send updated room list to all clients
                broadcastRoomList();
                
                // Remove empty rooms (except if it's the only room)
                if (room.isEmpty() && rooms.size() > 1) {
                    rooms.remove(roomId);
                    System.out.println("🗑️  Removed empty room: " + room.getRoomName());
                }
            }
        }
        
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }

    private static String generateWebSocketAccept(String key) throws NoSuchAlgorithmException {
        String combined = key + WEBSOCKET_MAGIC_STRING;
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(combined.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static void handleWebSocketMessages(Socket clientSocket) {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            
            while (!clientSocket.isClosed() && running) {
                byte[] buffer = new byte[8192];
                int bytesRead = inputStream.read(buffer);
                
                if (bytesRead == -1) {
                    break;
                }
                
                if (bytesRead >= 2) {
                    String message = decodeWebSocketFrame(buffer, bytesRead);
                    if (message != null && !message.isEmpty()) {
                        handleMessage(clientSocket, message);
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
            
            if (opcode != 0x1 || !fin) {
                return null;
            }
            
            int offset = 2;
            
            if (payloadLength == 126) {
                if (length < 4) return null;
                payloadLength = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                offset = 4;
            } else if (payloadLength == 127) {
                offset = 10;
            }
            
            byte[] maskingKey = new byte[4];
            if (masked) {
                if (length < offset + 4) return null;
                System.arraycopy(buffer, offset, maskingKey, 0, 4);
                offset += 4;
            }
            
            if (length < offset + payloadLength) {
                payloadLength = length - offset;
            }
            
            byte[] payload = new byte[payloadLength];
            System.arraycopy(buffer, offset, payload, 0, payloadLength);
            
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

    private static void handleMessage(Socket sender, String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();
            
            switch (type) {
                case "getRooms":
                    handleGetRooms(sender);
                    break;
                case "createRoom":
                    handleCreateRoom(sender, json);
                    break;
                case "joinRoom":
                    handleJoinRoom(sender, json);
                    break;
                case "draw":
                    handleDrawMessage(sender, message);
                    break;
                case "clear":
                    handleClearMessage(sender);
                    break;
                case "cursor":
                    handleCursorMessage(sender, message);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
            }
        } catch (JsonSyntaxException e) {
            System.err.println("Invalid JSON: " + message);
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleGetRooms(Socket sender) {
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomList");
        
        JsonArray roomArray = new JsonArray();
        for (Room room : rooms.values()) {
            JsonObject roomObj = new JsonObject();
            roomObj.addProperty("roomId", room.getRoomId());
            roomObj.addProperty("roomName", room.getRoomName());
            roomObj.addProperty("creator", room.getCreatorUsername());
            roomObj.addProperty("participants", room.getParticipantCount());
            roomObj.addProperty("maxParticipants", room.getMaxParticipants());
            roomArray.add(roomObj);
        }
        
        response.add("rooms", roomArray);
        sendWebSocketMessage(sender, response.toString());
    }

    private static void handleCreateRoom(Socket sender, JsonObject json) {
        String username = json.get("username").getAsString();
        String roomName = json.get("roomName").getAsString();
        String roomId = UUID.randomUUID().toString();
        
        Room newRoom = new Room(roomId, roomName, username);
        newRoom.addParticipant(username);
        rooms.put(roomId, newRoom);
        
        clients.put(sender, username);
        clientRooms.put(sender, roomId);
        
        System.out.println("🎨 " + username + " created room: " + roomName + " [" + roomId + "]");
        
        // Send success response
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomCreated");
        response.addProperty("roomId", roomId);
        response.addProperty("roomName", roomName);
        sendWebSocketMessage(sender, response.toString());
        
        // Broadcast updated room list
        broadcastRoomList();
    }

    private static void handleJoinRoom(Socket sender, JsonObject json) {
        String username = json.get("username").getAsString();
        String roomId = json.get("roomId").getAsString();
        
        Room room = rooms.get(roomId);
        
        if (room == null) {
            JsonObject error = new JsonObject();
            error.addProperty("type", "error");
            error.addProperty("message", "Room not found");
            sendWebSocketMessage(sender, error.toString());
            return;
        }
        
        if (room.isFull()) {
            JsonObject error = new JsonObject();
            error.addProperty("type", "error");
            error.addProperty("message", "Room is full");
            sendWebSocketMessage(sender, error.toString());
            return;
        }
        
        room.addParticipant(username);
        clients.put(sender, username);
        clientRooms.put(sender, roomId);
        
        System.out.println("👤 " + username + " joined room: " + room.getRoomName());
        
        // Send room joined confirmation
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomJoined");
        response.addProperty("roomId", roomId);
        response.addProperty("roomName", room.getRoomName());
        sendWebSocketMessage(sender, response.toString());
        
        // Send drawing history to new user
        for (String historyMessage : room.getDrawingHistory()) {
            sendWebSocketMessage(sender, historyMessage);
        }
        
        // Notify others in the room
        JsonObject joinNotification = new JsonObject();
        joinNotification.addProperty("type", "userJoined");
        joinNotification.addProperty("username", username);
        joinNotification.addProperty("roomId", roomId);
        broadcastToRoom(roomId, joinNotification.toString(), sender);
        
        // Broadcast updated room list
        broadcastRoomList();
    }

    private static void handleDrawMessage(Socket sender, String message) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) return;
        
        Room room = rooms.get(roomId);
        if (room == null) return;
        
        // Add to room's drawing history
        room.addToDrawingHistory(message);
        
        // Broadcast to room
        broadcastToRoom(roomId, message, sender);
    }

    private static void handleClearMessage(Socket sender) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) return;
        
        Room room = rooms.get(roomId);
        if (room == null) return;
        
        // Clear room's drawing history
        room.clearDrawingHistory();
        
        // Broadcast clear to room
        JsonObject clearMessage = new JsonObject();
        clearMessage.addProperty("type", "clear");
        clearMessage.addProperty("username", clients.get(sender));
        broadcastToRoom(roomId, clearMessage.toString(), sender);
        
        System.out.println("🗑️  Canvas cleared in room: " + room.getRoomName());
    }

    private static void handleCursorMessage(Socket sender, String message) {
        String roomId = clientRooms.get(sender);
        if (roomId == null) return;
        
        // Broadcast cursor position to room
        broadcastToRoom(roomId, message, sender);
    }

    private static void broadcastToRoom(String roomId, String message, Socket excludeSocket) {
        for (Map.Entry<Socket, String> entry : clientRooms.entrySet()) {
            if (entry.getValue().equals(roomId) && entry.getKey() != excludeSocket) {
                sendWebSocketMessage(entry.getKey(), message);
            }
        }
    }

    private static void broadcastRoomList() {
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomList");
        
        JsonArray roomArray = new JsonArray();
        for (Room room : rooms.values()) {
            JsonObject roomObj = new JsonObject();
            roomObj.addProperty("roomId", room.getRoomId());
            roomObj.addProperty("roomName", room.getRoomName());
            roomObj.addProperty("creator", room.getCreatorUsername());
            roomObj.addProperty("participants", room.getParticipantCount());
            roomObj.addProperty("maxParticipants", room.getMaxParticipants());
            roomArray.add(roomObj);
        }
        
        response.add("rooms", roomArray);
        
        // Send to all connected clients
        for (Socket client : clients.keySet()) {
            sendWebSocketMessage(client, response.toString());
        }
    }

    private static void sendWebSocketMessage(Socket client, String message) {
        try {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            OutputStream out = client.getOutputStream();
            
            out.write(0x81);
            
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
            
            out.write(payload);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
            clients.remove(client);
            clientRooms.remove(client);
        }
    }

    public static void stopServer() {
        running = false;
    }
    
    private static void displayNetworkAddresses() {
        try {
            System.out.println("\n🌐 Server Network Addresses:");
            System.out.println("   Local: ws://localhost:" + PORT);
            
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    if (address instanceof Inet4Address) {
                        System.out.println("   Network: ws://" + address.getHostAddress() + ":" + PORT);
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Error getting network addresses: " + e.getMessage());
        }
    }
}
