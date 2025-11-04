package org.example;

import org.example.client.WhiteboardClient;
import org.example.server.WhiteboardServer;

import javax.swing.*;
import java.awt.*;

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
            String localIP = WhiteboardServer.getLocalIPAddress();
            
            int confirm = JOptionPane.showConfirmDialog(
                null,
                "Starting Whiteboard Server on port 12345.\n\n" +
                "Share this IP with others to connect:\n" +
                localIP + ":12345\n\n" +
                "The server will run until you close this application.\n" +
                "Continue?",
                "Start Server",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE
            );
            
            if (confirm == JOptionPane.YES_OPTION) {
                WhiteboardServer server = new WhiteboardServer();
                
                // Start server in a separate thread
                Thread serverThread = new Thread(() -> server.start());
                serverThread.start();
                
                // Show server status window
                JFrame serverFrame = new JFrame("Whiteboard Server");
                serverFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                serverFrame.setSize(500, 300);
                serverFrame.setLocationRelativeTo(null);
                
                JTextArea statusArea = new JTextArea();
                statusArea.setEditable(false);
                statusArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
                statusArea.setText(
                    "╔════════════════════════════════════════════╗\n" +
                    "║     WHITEBOARD SERVER IS RUNNING          ║\n" +
                    "╚════════════════════════════════════════════╝\n\n" +
                    "Server Port: 12345\n\n" +
                    "CONNECTION INSTRUCTIONS:\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                    "For LOCAL connection (same computer):\n" +
                    "  Use: localhost\n\n" +
                    "For NETWORK connection (other devices):\n" +
                    "  Use: " + localIP + "\n\n" +
                    "Make sure all devices are on the same WiFi!\n\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                    "Close this window to stop the server."
                );
                
                JScrollPane scrollPane = new JScrollPane(statusArea);
                serverFrame.add(scrollPane);
                
                // Add shutdown hook
                serverFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        server.stop();
                    }
                });
                
                serverFrame.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
    
    private static void startClient() {
        System.out.println("Starting Whiteboard Client...");
        
        // Use the existing client main method which now has the connection dialog
        WhiteboardClient.main(new String[]{});
    }
    
    private static void printUsage() {
        System.out.println("Usage: java -jar whiteboard.jar [server|client]");
        System.out.println("  server - Start the whiteboard server");
        System.out.println("  client - Start the whiteboard client");
        System.out.println("  (no args) - Show selection dialog");
    }
}