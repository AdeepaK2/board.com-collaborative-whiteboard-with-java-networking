package org.example.client;

import org.example.model.DrawingMessage;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

/**
 * Network client that connects to the whiteboard server
 * and handles communication between GUI and server
 */
public class WhiteboardClient {
    private String serverHost;
    private int serverPort;
    
    private Socket socket;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private String username;
    private WhiteboardGUI gui;
    private boolean connected = false;
    
    public WhiteboardClient(String username, String serverHost, int serverPort) {
        this.username = username;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }
    
    /**
     * Connect to the whiteboard server
     */
    public boolean connect() {
        try {
            socket = new Socket(serverHost, serverPort);
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
            
            System.out.println("Connected to server at " + serverHost + ":" + serverPort + " as " + username);
            return true;
            
        } catch (IOException e) {
            System.err.println("Failed to connect to server at " + serverHost + ":" + serverPort + " - " + e.getMessage());
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
            // Create custom connection dialog
            JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
            JTextField usernameField = new JTextField("User" + System.currentTimeMillis() % 1000);
            JTextField serverField = new JTextField("localhost");
            JTextField portField = new JTextField("12345");
            
            panel.add(new JLabel("Username:"));
            panel.add(usernameField);
            panel.add(new JLabel("Server IP:"));
            panel.add(serverField);
            panel.add(new JLabel("Port:"));
            panel.add(portField);
            
            int result = JOptionPane.showConfirmDialog(
                null,
                panel,
                "Connect to Whiteboard Server",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (result == JOptionPane.OK_OPTION) {
                String username = usernameField.getText().trim();
                String serverHost = serverField.getText().trim();
                String portStr = portField.getText().trim();
                
                if (username.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        null,
                        "Username cannot be empty!",
                        "Invalid Input",
                        JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                
                int port;
                try {
                    port = Integer.parseInt(portStr);
                    if (port < 1 || port > 65535) {
                        throw new NumberFormatException("Port out of range");
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(
                        null,
                        "Invalid port number! Please enter a number between 1 and 65535.",
                        "Invalid Port",
                        JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                
                // Create client and connect
                WhiteboardClient client = new WhiteboardClient(username, serverHost, port);
                if (client.connect()) {
                    // Create and show GUI
                    WhiteboardGUI gui = new WhiteboardGUI(client);
                    client.setGUI(gui);
                    gui.setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(
                        null,
                        "Failed to connect to server at " + serverHost + ":" + port + "\n\n" +
                        "Please make sure:\n" +
                        "1. The server is running\n" +
                        "2. The IP address is correct\n" +
                        "3. Both devices are on the same network\n" +
                        "4. Firewall allows connections on port " + port,
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
    }
}