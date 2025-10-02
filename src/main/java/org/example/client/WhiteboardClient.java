package org.example.client;

import org.example.model.DrawingMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

/**
 * Network client that connects to the whiteboard server
 * and handles communication between GUI and server
 */
public class WhiteboardClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    
    private Socket socket;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private String username;
    private WhiteboardGUI gui;
    private boolean connected = false;
    
    public WhiteboardClient(String username) {
        this.username = username;
    }
    
    /**
     * Connect to the whiteboard server
     */
    public boolean connect() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());
            connected = true;
            
            // Send join message
            DrawingMessage joinMessage = new DrawingMessage(DrawingMessage.MessageType.USER_JOIN, username);
            sendMessage(joinMessage);
            
            // Start listening for messages from server
            Thread messageListener = new Thread(this::listenForMessages);
            messageListener.setDaemon(true);
            messageListener.start();
            
            System.out.println("Connected to server as " + username);
            return true;
            
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Disconnect from the server
     */
    public void disconnect() {
        connected = false;
        try {
            if (username != null) {
                DrawingMessage leaveMessage = new DrawingMessage(DrawingMessage.MessageType.USER_LEAVE, username);
                sendMessage(leaveMessage);
            }
            
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
            
            System.out.println("Disconnected from server");
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }
    
    /**
     * Send a message to the server
     */
    public synchronized void sendMessage(DrawingMessage message) {
        if (connected && output != null) {
            try {
                message.setUsername(username);
                output.writeObject(message);
                output.flush();
            } catch (IOException e) {
                System.err.println("Error sending message: " + e.getMessage());
                connected = false;
            }
        }
    }
    
    /**
     * Listen for messages from the server
     */
    private void listenForMessages() {
        while (connected) {
            try {
                Object message = input.readObject();
                if (message instanceof DrawingMessage) {
                    DrawingMessage drawingMsg = (DrawingMessage) message;
                    
                    // Handle different message types
                    if (gui != null) {
                        SwingUtilities.invokeLater(() -> gui.handleServerMessage(drawingMsg));
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (connected) {
                    System.err.println("Error receiving message: " + e.getMessage());
                    connected = false;
                }
                break;
            }
        }
    }
    
    /**
     * Set the GUI reference
     */
    public void setGUI(WhiteboardGUI gui) {
        this.gui = gui;
    }
    
    /**
     * Check if connected to server
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * Get the username
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Main method to start the client
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Get username from user
            String username = JOptionPane.showInputDialog(
                null,
                "Enter your username:",
                "Join Whiteboard",
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (username != null && !username.trim().isEmpty()) {
                username = username.trim();
                
                // Create client and connect
                WhiteboardClient client = new WhiteboardClient(username);
                if (client.connect()) {
                    // Create and show GUI
                    WhiteboardGUI gui = new WhiteboardGUI(client);
                    client.setGUI(gui);
                    gui.setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(
                        null,
                        "Failed to connect to server. Please make sure the server is running.",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
    }
}