package org.example.server;

import com.google.gson.JsonObject;
import org.example.model.Room;
import org.example.server.modules.MessageHandler;
import org.example.server.modules.MessageHandler.MessageResult;
import org.example.server.modules.RoomManager;
import org.example.server.modules.WebSocketHandler;
import org.example.server.modules.NetworkUtil;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main WebSocket Server for Collaborative Whiteboard
 * 
 * NETWORKING LESSONS DEMONSTRATED:
 * - LESSON 2: Client-Server Architecture, Socket Programming
 * - LESSON 3: TCP/IP reliable connections
 * - LESSON 5: Network addressing and interfaces
 * - LESSON 6: Multithreaded server with thread-safe collections
 * - LESSON 9: HTTP protocol (WebSocket handshake)
 * 
 * Architecture: Single server class using modular components
 * - RoomManager: Handles room operations
 * - MessageHandler: Processes application messages
 * - WebSocketHandler: Handles WebSocket protocol
 * - NetworkUtil: Network discovery utilities
 */
public class Server {
    private static final int PORT = 8080;
    
    // LESSON 6: Thread-safe collections for concurrent access
    private static final Map<Socket, String> clients = new ConcurrentHashMap<>();
    private static final Map<Socket, String> clientRooms = new ConcurrentHashMap<>();
    
    // Feature modules
    private static final RoomManager roomManager = new RoomManager();
    private static final MessageHandler messageHandler = new MessageHandler(roomManager);
    
    private static volatile boolean running = true;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("🎨 Whiteboard Server");
        System.out.println("=================================================");
        
        // LESSON 5: Display all network addresses
        NetworkUtil.displayNetworkAddresses(PORT);
        
        System.out.println("=================================================");
        System.out.println("Waiting for connections...\n");
        
        // LESSON 3: Create TCP server socket
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (running) {
                try {
                    // LESSON 3: Accept incoming client connections
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New connection from: " + clientSocket.getInetAddress());
                    
                    // LESSON 6: Handle each client in separate thread
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

    /**
     * LESSON 6: Client handler - runs in separate thread per client
     */
    private static void handleClient(Socket clientSocket) {
        try {
            // LESSON 3: Setup input/output streams
            BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );
            OutputStream out = clientSocket.getOutputStream();

            // LESSON 9: Read HTTP headers for WebSocket handshake
            String line;
            String key = null;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Sec-WebSocket-Key:")) {
                    key = line.substring(19).trim();
                }
            }

            if (key != null) {
                // Generate WebSocket accept key
                String accept = WebSocketHandler.generateWebSocketAccept(key);
                
                // LESSON 9: Send HTTP 101 Switching Protocols response
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

    /**
     * Handle incoming WebSocket messages
     */
    private static void handleWebSocketMessages(Socket clientSocket) {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            
            while (!clientSocket.isClosed() && running) {
                byte[] buffer = new byte[8192];
                int bytesRead = inputStream.read(buffer);
                
                if (bytesRead == -1) {
                    break; // Client disconnected
                }
                
                if (bytesRead >= 2) {
                    // Decode WebSocket frame
                    String message = WebSocketHandler.decodeWebSocketFrame(buffer, bytesRead);
                    
                    if (message != null && !message.isEmpty()) {
                        // Process message using MessageHandler
                        MessageResult result = messageHandler.handleMessage(
                            clientSocket, message, clients, clientRooms
                        );
                        
                        // Execute result action
                        executeMessageResult(clientSocket, result);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error in WebSocket message handling: " + e.getMessage());
        }
    }

    /**
     * Execute the result of message processing
     */
    private static void executeMessageResult(Socket sender, MessageResult result) {
        switch (result.action) {
            case SEND_TO_SENDER:
                sendMessage(sender, result.message);
                break;
                
            case BROADCAST_TO_ROOM:
                broadcastToRoom(result.roomId, result.message, sender);
                break;
                
            case JOIN_ROOM:
                // Send confirmation to sender
                sendMessage(sender, result.message);
                
                // Send drawing history
                if (result.history != null) {
                    for (String msg : result.history) {
                        sendMessage(sender, msg);
                    }
                }
                
                // Notify others in room
                if (result.broadcastMessage != null) {
                    broadcastToRoom(result.roomId, result.broadcastMessage, sender);
                }
                
                // Broadcast updated room list
                broadcastRoomList();
                break;
                
            case BROADCAST_ROOM_LIST:
                sendMessage(sender, result.message);
                broadcastRoomList();
                break;
                
            case ERROR:
                sendMessage(sender, result.message);
                break;
                
            case NO_ACTION:
            default:
                break;
        }
    }

    /**
     * Clean up when client disconnects
     */
    private static void cleanupClient(Socket clientSocket) {
        String username = clients.remove(clientSocket);
        String roomId = clientRooms.remove(clientSocket);
        
        if (username != null && roomId != null) {
            Room room = roomManager.getRoom(roomId);
            if (room != null) {
                room.removeParticipant(username);
                System.out.println("👋 " + username + " left room: " + room.getRoomName());
                
                // Notify other users in the room
                JsonObject leaveMessage = new JsonObject();
                leaveMessage.addProperty("type", "userLeft");
                leaveMessage.addProperty("username", username);
                broadcastToRoom(roomId, leaveMessage.toString(), clientSocket);
                
                // Broadcast updated room list
                broadcastRoomList();
                
                // Clean up empty rooms
                roomManager.cleanupEmptyRooms();
            }
        }
        
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }

    /**
     * Broadcast message to all clients in a room
     */
    private static void broadcastToRoom(String roomId, String message, Socket excludeSocket) {
        for (Map.Entry<Socket, String> entry : clientRooms.entrySet()) {
            if (entry.getValue().equals(roomId) && entry.getKey() != excludeSocket) {
                sendMessage(entry.getKey(), message);
            }
        }
    }

    /**
     * Broadcast room list to all connected clients
     */
    private static void broadcastRoomList() {
        JsonObject response = new JsonObject();
        response.addProperty("type", "roomList");
        response.add("rooms", messageHandler.getRoomsAsJson());
        
        String message = response.toString();
        for (Socket client : clients.keySet()) {
            sendMessage(client, message);
        }
    }

    /**
     * Send message to a specific client
     */
    private static void sendMessage(Socket client, String message) {
        try {
            OutputStream out = client.getOutputStream();
            byte[] frame = WebSocketHandler.encodeWebSocketFrame(message);
            out.write(frame);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
            clients.remove(client);
            clientRooms.remove(client);
        }
    }

    /**
     * Stop the server
     */
    public static void stopServer() {
        running = false;
    }
}
