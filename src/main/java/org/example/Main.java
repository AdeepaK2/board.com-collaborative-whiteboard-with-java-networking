package org.example;

import org.example.client.WhiteboardClient;
import org.example.server.WhiteboardServer;

import javax.swing.*;

/**
 * Main entry point for the Live Whiteboard Application
 * Provides options to start either server or client
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Live Whiteboard Application");
        System.out.println("==========================");
        
        // If command line arguments are provided, use them
        if (args.length > 0) {
            String mode = args[0].toLowerCase();
            
            if ("server".equals(mode)) {
                startServer();
            } else if ("client".equals(mode)) {
                startClient();
            } else {
                printUsage();
            }
        } else {
            // Show GUI dialog to choose mode
            showModeSelection();
        }
    }
    
    private static void showModeSelection() {
        SwingUtilities.invokeLater(() -> {
            String[] options = {"Start Server", "Start Client", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                null,
                "Choose how to start the Live Whiteboard Application:",
                "Live Whiteboard",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[1] // Default to client
            );
            
            switch (choice) {
                case 0: // Start Server
                    startServer();
                    break;
                case 1: // Start Client
                    startClient();
                    break;
                default: // Cancel or close
                    System.exit(0);
                    break;
            }
        });
    }
    
    private static void startServer() {
        System.out.println("Starting Whiteboard Server...");
        
        SwingUtilities.invokeLater(() -> {
            int confirm = JOptionPane.showConfirmDialog(
                null,
                "Starting Whiteboard Server on port 12345.\n" +
                "The server will run until you close this application.\n" +
                "Continue?",
                "Start Server",
                JOptionPane.YES_NO_OPTION
            );
            
            if (confirm == JOptionPane.YES_OPTION) {
                // Start server in a separate thread
                Thread serverThread = new Thread(() -> {
                    WhiteboardServer server = new WhiteboardServer();
                    server.start();
                });
                serverThread.start();
                
                // Show server status window
                JFrame serverFrame = new JFrame("Whiteboard Server");
                serverFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                serverFrame.setSize(400, 200);
                serverFrame.setLocationRelativeTo(null);
                
                JLabel statusLabel = new JLabel("<html><center>Whiteboard Server is running<br>Port: 12345<br><br>Close this window to stop the server</center></html>");
                statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
                serverFrame.add(statusLabel);
                
                serverFrame.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
    
    private static void startClient() {
        System.out.println("Starting Whiteboard Client...");
        
        // Use the existing client main method
        SwingUtilities.invokeLater(() -> {
            String username = JOptionPane.showInputDialog(
                null,
                "Enter your username:",
                "Join Whiteboard",
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (username != null && !username.trim().isEmpty()) {
                username = username.trim();
                
                WhiteboardClient client = new WhiteboardClient(username);
                if (client.connect()) {
                    // Success will be handled by WhiteboardClient
                    WhiteboardClient.main(new String[]{username});
                } else {
                    JOptionPane.showMessageDialog(
                        null,
                        "Failed to connect to server.\n" +
                        "Please make sure the server is running on localhost:12345",
                        "Connection Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                    
                    // Offer to start server instead
                    int choice = JOptionPane.showConfirmDialog(
                        null,
                        "Would you like to start a server instead?",
                        "Start Server?",
                        JOptionPane.YES_NO_OPTION
                    );
                    
                    if (choice == JOptionPane.YES_OPTION) {
                        startServer();
                    }
                }
            }
        });
    }
    
    private static void printUsage() {
        System.out.println("Usage: java -jar whiteboard.jar [server|client]");
        System.out.println("  server - Start the whiteboard server");
        System.out.println("  client - Start the whiteboard client");
        System.out.println("  (no args) - Show selection dialog");
    }
}