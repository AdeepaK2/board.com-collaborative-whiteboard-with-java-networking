package org.example.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket server for the whiteboard application
 * Handles real-time communication with React frontend
 */
public class WebSocketWhiteboardServer {
    private static final int PORT = 8080;
    private static final String WEBSOCKET_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final Map<Socket, String> clients = new ConcurrentHashMap<>();
    private static final List<String> drawingHistory = new ArrayList<>();
    private static final Gson gson = new Gson();
    private static volatile boolean running = true;

    public static void main(String[] args) {
        System.out.println("Starting Whiteboard WebSocket Server on port " + PORT);
        System.out.println("React frontend should connect to: ws://localhost:" + PORT);
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());
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
                System.out.println("Header: " + line);
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
                
                System.out.println("WebSocket handshake completed");

                // Handle WebSocket messages
                handleWebSocketMessages(clientSocket);
            }
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            String username = clients.remove(clientSocket);
            if (username != null) {
                System.out.println("Client disconnected: " + username);
                // Notify other clients
                JsonObject leaveMessage = new JsonObject();
                leaveMessage.addProperty("type", "userLeft");
                leaveMessage.addProperty("username", username);
                broadcastMessage(leaveMessage.toString(), clientSocket);
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
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
                byte[] buffer = new byte[4096];
                int bytesRead = inputStream.read(buffer);
                
                if (bytesRead == -1) {
                    break; // Client disconnected
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

    private static void handleMessage(Socket sender, String message) {
        try {
            System.out.println("Received message: " + message);
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();
            
            switch (type) {
                case "join":
                    handleJoinMessage(sender, json);
                    break;
                case "draw":
                    handleDrawMessage(sender, message);
                    break;
                case "clear":
                    handleClearMessage(sender);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
            }
        } catch (JsonSyntaxException e) {
            System.err.println("Invalid JSON message: " + message);
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
        }
    }

    private static void handleJoinMessage(Socket sender, JsonObject json) {
        String username = json.get("username").getAsString();
        clients.put(sender, username);
        System.out.println("User joined: " + username + " (Total users: " + clients.size() + ")");
        
        // Send drawing history to new user
        for (String historyMessage : drawingHistory) {
            sendWebSocketMessage(sender, historyMessage);
        }
        
        // Broadcast user joined to others
        JsonObject response = new JsonObject();
        response.addProperty("type", "userJoined");
        response.addProperty("username", username);
        broadcastMessage(response.toString(), sender);
    }

    private static void handleDrawMessage(Socket sender, String message) {
        // Add to drawing history
        drawingHistory.add(message);
        
        // Broadcast to all other clients
        broadcastMessage(message, sender);
        System.out.println("Broadcasted drawing message from " + clients.get(sender));
    }

    private static void handleClearMessage(Socket sender) {
        // Clear drawing history
        drawingHistory.clear();
        
        // Broadcast clear command to all other clients
        JsonObject clearMessage = new JsonObject();
        clearMessage.addProperty("type", "clear");
        clearMessage.addProperty("username", clients.get(sender));
        broadcastMessage(clearMessage.toString(), sender);
        System.out.println("Canvas cleared by " + clients.get(sender));
    }

    private static void broadcastMessage(String message, Socket sender) {
        for (Socket client : clients.keySet()) {
            if (client != sender && !client.isClosed()) {
                sendWebSocketMessage(client, message);
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
                // Write 64-bit length (simplified for small messages)
                for (int i = 0; i < 8; i++) {
                    out.write(0);
                }
            }
            
            // Write payload
            out.write(payload);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error sending message to client: " + e.getMessage());
            clients.remove(client);
        }
    }

    public static void stopServer() {
        running = false;
    }
}