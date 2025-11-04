package org.example;

import org.example.server.MultiRoomWebSocketServer;

/**
 * Main entry point for the Multi-Room Whiteboard WebSocket Server
 * This server communicates with web-based clients (React/TypeScript frontend)
 * via WebSocket protocol on port 8080
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Multi-Room Whiteboard Server");
        System.out.println("  Version 2.0");
        System.out.println("========================================\n");

        // Start the multi-room WebSocket server
        MultiRoomWebSocketServer.main(args);
    }
}
