package org.example;

import org.example.server.Server;

/**
 * Main entry point for the Whiteboard Server
 * Starts the WebSocket server on port 8080 for React/TypeScript frontend
 */
public class Main {
    public static void main(String[] args) {
        // Start the server
        Server.main(args);
    }
}
