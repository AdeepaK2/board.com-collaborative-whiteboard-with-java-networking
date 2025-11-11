package org.example.server;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
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
import java.util.concurrent.Executors;

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
    private static final int HTTP_PORT = 8081;
    
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
        
        // Start HTTP API server for board save/load
        startHttpApiServer();
        
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
     * Start HTTP API server for REST endpoints
     */
    private static void startHttpApiServer() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            
            // Board API endpoints
            httpServer.createContext("/api/boards", new BoardApiHandler(roomManager));
            
            // Authentication API endpoints
            httpServer.createContext("/api/auth", new AuthApiHandler(messageHandler.getUserDB()));
            
            // Static file serving for images
            httpServer.createContext("/images", new StaticFileHandler("saved_boards/images"));
            
            httpServer.setExecutor(Executors.newFixedThreadPool(4));
            httpServer.start();
            System.out.println("📡 HTTP API Server started on port " + HTTP_PORT);
            System.out.println("   - Board API: http://localhost:" + HTTP_PORT + "/api/boards");
            System.out.println("   - Auth API: http://localhost:" + HTTP_PORT + "/api/auth");
            System.out.println("   - Images: http://localhost:" + HTTP_PORT + "/images/");
        } catch (IOException e) {
            System.err.println("Failed to start HTTP API server: " + e.getMessage());
        }
    }

    /**
     * LESSON 6: Client handler - runs in separate thread per client
     * Handles both WebSocket connections and HTTP GET requests for static files
     */
    private static void handleClient(Socket clientSocket) {
        BufferedReader in = null;
        try {
            // LESSON 3: Setup input/output streams
            in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream())
            );
            OutputStream out = clientSocket.getOutputStream();

            // Mark the stream so we can reset if needed
            // Read first line to determine request type
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                clientSocket.close();
                return;
            }

            // Check if it's an HTTP GET request for static files
            if (requestLine.startsWith("GET /images/")) {
                handleStaticFileRequest(clientSocket, requestLine, in, out);
                return;
            }

            // Check if it's a WebSocket upgrade request
            // Read all headers into a list first
            java.util.List<String> headers = new java.util.ArrayList<>();
            headers.add(requestLine);
            
            String line;
            String key = null;
            boolean isWebSocketRequest = false;
            
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                headers.add(line);
                if (line.startsWith("Upgrade:") && line.contains("websocket")) {
                    isWebSocketRequest = true;
                }
                if (line.startsWith("Sec-WebSocket-Key:")) {
                    key = line.substring(19).trim();
                }
            }

            // If it's a WebSocket handshake request
            if (isWebSocketRequest && key != null) {
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
            } else {
                // Not a WebSocket handshake or static file request, close connection
                sendHttpError(out, 400, "Bad Request");
                clientSocket.close();
            }
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException ex) {
                // Ignore
            }
        } finally {
            // Only cleanup if it's a WebSocket connection (not static file request)
            // Static file requests close the connection themselves
            if (!clientSocket.isClosed()) {
                cleanupClient(clientSocket);
            }
        }
    }

    /**
     * Handle HTTP GET request for static files (images)
     * Serves files from saved_boards/images/ directory
     */
    private static void handleStaticFileRequest(Socket clientSocket, String requestLine, 
                                               BufferedReader in, OutputStream out) {
        try {
            // Parse request path
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendHttpError(out, 400, "Bad Request");
                return;
            }

            String path = parts[1];
            
            // Extract filename from path (/images/filename.png)
            if (!path.startsWith("/images/")) {
                sendHttpError(out, 404, "Not Found");
                return;
            }

            String filename = path.substring("/images/".length());
            
            // Security: Prevent directory traversal
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                sendHttpError(out, 403, "Forbidden");
                return;
            }

            // Read remaining headers (browsers may send additional headers)
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Consume headers
            }

            // Build file path
            java.nio.file.Path filePath = java.nio.file.Paths.get("saved_boards", "images", filename);

            // Check if file exists
            if (!java.nio.file.Files.exists(filePath) || !java.nio.file.Files.isRegularFile(filePath)) {
                sendHttpError(out, 404, "Not Found");
                return;
            }

            // Read file
            byte[] fileData = java.nio.file.Files.readAllBytes(filePath);

            // Determine content type
            String contentType = getContentType(filename);

            // Send HTTP response
            String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + contentType + "\r\n" +
                            "Content-Length: " + fileData.length + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Cache-Control: public, max-age=3600\r\n" +
                            "\r\n";

            out.write(response.getBytes());
            out.write(fileData);
            out.flush();

            System.out.println("📁 Served static file: " + filename + " (" + fileData.length + " bytes)");
            
        } catch (Exception e) {
            System.err.println("Error serving static file: " + e.getMessage());
            try {
                sendHttpError(out, 500, "Internal Server Error");
            } catch (Exception ex) {
                // Ignore
            }
        } finally {
            try {
                clientSocket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Send HTTP error response
     */
    private static void sendHttpError(OutputStream out, int statusCode, String statusText) throws IOException {
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                         "Content-Type: text/plain\r\n" +
                         "Content-Length: " + statusText.length() + "\r\n" +
                         "\r\n" +
                         statusText;
        out.write(response.getBytes());
        out.flush();
    }

    /**
     * Get content type based on file extension
     */
    private static String getContentType(String filename) {
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerName.endsWith(".png")) {
            return "image/png";
        } else if (lowerName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerName.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
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

            case BROADCAST_NEW_PUBLIC_ROOM:
                // Send confirmation to creator
                sendMessage(sender, result.message);

                // Broadcast notification to ALL connected clients
                if (result.broadcastMessage != null) {
                    for (Socket client : clients.keySet()) {
                        sendMessage(client, result.broadcastMessage);
                    }
                }

                // Broadcast updated room list
                broadcastRoomList();
                break;

            case MULTICAST_TO_INVITED_USERS:
                // Send confirmation to creator
                sendMessage(sender, result.message);

                // Multicast notification to invited users only
                if (result.broadcastMessage != null && result.invitedUsers != null) {
                    for (Map.Entry<Socket, String> entry : clients.entrySet()) {
                        String username = entry.getValue();
                        if (result.invitedUsers.contains(username)) {
                            sendMessage(entry.getKey(), result.broadcastMessage);
                        }
                    }
                }

                // Broadcast updated room list to all users (each gets their personalized list)
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
     * Public method to broadcast message to all clients in a room
     * Called from HTTP REST API handlers (e.g., BoardApiHandler)
     * 
     * @param roomId The room ID to broadcast to
     * @param message The JSON message to broadcast
     */
    public static void broadcastToRoomPublic(String roomId, String message) {
        broadcastToRoom(roomId, message, null);
    }

    /**
     * Get RoomManager instance (for finding rooms by name)
     */
    public static RoomManager getRoomManager() {
        return roomManager;
    }

    /**
     * Broadcast room list to all connected clients
     * Each client receives a personalized list (public rooms + rooms they're invited to)
     */
    private static void broadcastRoomList() {
        for (Map.Entry<Socket, String> entry : clients.entrySet()) {
            Socket client = entry.getKey();
            String username = entry.getValue();
            
            if (username != null) {
                JsonObject response = new JsonObject();
                response.addProperty("type", "roomList");
                response.add("rooms", messageHandler.getRoomsAsJsonForUser(username));
                
                sendMessage(client, response.toString());
            }
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
