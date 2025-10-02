package org.example.server;

import org.example.model.DrawingMessage;

import java.io.*;
import java.net.*;

/**
 * Handles individual client connections and manages communication
 * between the client and the whiteboard server
 */
public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private WhiteboardServer server;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private String username;
    private boolean connected = true;
    
    public ClientHandler(Socket clientSocket, WhiteboardServer server) {
        this.clientSocket = clientSocket;
        this.server = server;
        
        try {
            // Setup input/output streams
            output = new ObjectOutputStream(clientSocket.getOutputStream());
            input = new ObjectInputStream(clientSocket.getInputStream());
            
            System.out.println("ClientHandler created for: " + clientSocket.getInetAddress());
        } catch (IOException e) {
            System.err.println("Error setting up client streams: " + e.getMessage());
            close();
        }
    }
    
    @Override
    public void run() {
        try {
            // First message should be user joining with username
            Object firstMessage = input.readObject();
            if (firstMessage instanceof DrawingMessage) {
                DrawingMessage joinMsg = (DrawingMessage) firstMessage;
                if (joinMsg.getType() == DrawingMessage.MessageType.USER_JOIN) {
                    username = joinMsg.getUsername();
                    server.addClient(username, this);
                    System.out.println("User joined: " + username);
                }
            }
            
            // Listen for messages from client
            while (connected && !clientSocket.isClosed()) {
                try {
                    Object message = input.readObject();
                    if (message instanceof DrawingMessage) {
                        DrawingMessage drawingMsg = (DrawingMessage) message;
                        
                        // Validate message has username
                        if (drawingMsg.getUsername() == null) {
                            drawingMsg.setUsername(username);
                        }
                        
                        // Broadcast message to other clients
                        server.broadcastMessage(drawingMsg, username);
                    }
                } catch (ClassNotFoundException e) {
                    System.err.println("Invalid message type received from " + username);
                } catch (EOFException e) {
                    // Client disconnected
                    System.out.println("Client " + username + " disconnected");
                    break;
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("Error handling client " + username + ": " + e.getMessage());
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Invalid message format from client " + username);
        } finally {
            cleanup();
        }
    }
    
    /**
     * Send a message to this client
     */
    public synchronized void sendMessage(DrawingMessage message) {
        if (connected && output != null) {
            try {
                output.writeObject(message);
                output.flush();
            } catch (IOException e) {
                System.err.println("Error sending message to " + username + ": " + e.getMessage());
                close();
            }
        }
    }
    
    /**
     * Get the client's IP address
     */
    public String getClientAddress() {
        return clientSocket.getInetAddress().getHostAddress();
    }
    
    /**
     * Get the username of this client
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Check if client is connected
     */
    public boolean isConnected() {
        return connected && !clientSocket.isClosed();
    }
    
    /**
     * Close the client connection
     */
    public void close() {
        connected = false;
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing client connection: " + e.getMessage());
        }
    }
    
    /**
     * Cleanup when client disconnects
     */
    private void cleanup() {
        if (username != null) {
            server.removeClient(username);
        }
        close();
    }
    
    @Override
    public String toString() {
        return "ClientHandler{" +
                "username='" + username + '\'' +
                ", address=" + getClientAddress() +
                ", connected=" + connected +
                '}';
    }
}