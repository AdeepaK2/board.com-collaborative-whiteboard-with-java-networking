package org.example.server;

import org.example.model.DrawingMessage;
import org.example.model.User;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Whiteboard Server that handles multiple client connections
 * and synchronizes drawing operations across all connected clients
 */
public class WhiteboardServer {
    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private boolean running = false;
    
    // Thread-safe collections for managing clients and drawing history
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final List<DrawingMessage> drawingHistory = new CopyOnWriteArrayList<>();
    private final Set<User> activeUsers = ConcurrentHashMap.newKeySet();
    
    public WhiteboardServer() {
        System.out.println("Whiteboard Server initialized");
    }
    
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;
            System.out.println("Whiteboard Server started on port " + PORT);
            System.out.println("Waiting for client connections...");
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());
                    
                    // Create and start client handler thread
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    Thread clientThread = new Thread(clientHandler);
                    clientThread.start();
                    
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            // Close all client connections
            for (ClientHandler client : clients.values()) {
                client.close();
            }
            clients.clear();
            activeUsers.clear();
        } catch (IOException e) {
            System.err.println("Error stopping server: " + e.getMessage());
        }
        System.out.println("Server stopped");
    }
    
    /**
     * Broadcast a message to all connected clients except the sender
     */
    public synchronized void broadcastMessage(DrawingMessage message, String senderUsername) {
        // Add to drawing history if it's a drawing operation
        if (message.getType() == DrawingMessage.MessageType.DRAW || 
            message.getType() == DrawingMessage.MessageType.TEXT ||
            message.getType() == DrawingMessage.MessageType.ERASE) {
            drawingHistory.add(message);
        }
        
        // Clear history if clear command
        if (message.getType() == DrawingMessage.MessageType.CLEAR) {
            drawingHistory.clear();
        }
        
        // Broadcast to all clients except sender
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(senderUsername)) {
                entry.getValue().sendMessage(message);
            }
        }
        
        System.out.println("Broadcasted message: " + message.getType() + " from " + senderUsername);
    }
    
    /**
     * Add a new client to the server
     */
    public synchronized void addClient(String username, ClientHandler clientHandler) {
        clients.put(username, clientHandler);
        
        User user = new User(username, clientHandler.getClientAddress());
        activeUsers.add(user);
        
        // Send drawing history to new client
        for (DrawingMessage msg : drawingHistory) {
            clientHandler.sendMessage(msg);
        }
        
        // Notify all clients about new user
        DrawingMessage joinMessage = new DrawingMessage(DrawingMessage.MessageType.USER_JOIN, username);
        broadcastMessage(joinMessage, username);
        
        System.out.println("Client added: " + username + " (Total clients: " + clients.size() + ")");
    }
    
    /**
     * Remove a client from the server
     */
    public synchronized void removeClient(String username) {
        ClientHandler removed = clients.remove(username);
        if (removed != null) {
            activeUsers.removeIf(user -> user.getUsername().equals(username));
            
            // Notify all clients about user leaving
            DrawingMessage leaveMessage = new DrawingMessage(DrawingMessage.MessageType.USER_LEAVE, username);
            broadcastMessage(leaveMessage, username);
            
            System.out.println("Client removed: " + username + " (Total clients: " + clients.size() + ")");
        }
    }
    
    /**
     * Get list of active users
     */
    public Set<User> getActiveUsers() {
        return new HashSet<>(activeUsers);
    }
    
    /**
     * Get number of connected clients
     */
    public int getClientCount() {
        return clients.size();
    }
    
    public static void main(String[] args) {
        WhiteboardServer server = new WhiteboardServer();
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            server.stop();
        }));
        
        server.start();
    }
}