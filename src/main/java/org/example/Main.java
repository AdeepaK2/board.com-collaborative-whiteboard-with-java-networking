package org.example;

import org.example.server.WebSocketWhiteboardServer;

/**
 * Main entry point for the Whiteboard WebSocket Server
 * This server communicates with web-based clients (React/TypeScript frontend)
 * via WebSocket protocol on port 8080
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Whiteboard WebSocket Server");
        System.out.println("  Version 1.0");
        System.out.println("========================================\n");

        // Start the WebSocket server
        WebSocketWhiteboardServer.main(args);
    }
}
