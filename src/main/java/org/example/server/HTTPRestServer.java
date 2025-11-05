package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.example.model.Board;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * HTTP REST API Server for board save/load operations
 * Demonstrates TCP, URI/REST principles, and multithreading with thread pool
 */
public class HTTPRestServer {
    private static final int PORT = 8081;
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private static final BoardStorageService storageService = new BoardStorageService();
    private static final Gson gson = new Gson();
    private static volatile boolean running = true;
    
    public static void main(String[] args) {
        System.out.println("Starting HTTP REST Server on port " + PORT);
        System.out.println("Endpoints:");
        System.out.println("  POST   http://localhost:" + PORT + "/api/boards       - Save a board");
        System.out.println("  GET    http://localhost:" + PORT + "/api/boards       - List all boards");
        System.out.println("  GET    http://localhost:" + PORT + "/api/boards/{id}  - Load a board by ID");
        System.out.println("  DELETE http://localhost:" + PORT + "/api/boards/{id}  - Delete a board");
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New HTTP request from: " + clientSocket.getInetAddress());
                    
                    // Handle each request in a separate thread from the pool
                    threadPool.execute(() -> handleRequest(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }
    
    private static void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {
            
            // Parse HTTP request
            String requestLine = in.readLine();
            if (requestLine == null) {
                return;
            }
            
            System.out.println("Request: " + requestLine);
            
            // Parse request line
            String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                sendResponse(out, 400, "Bad Request", "Invalid HTTP request");
                return;
            }
            
            String method = parts[0];
            String uri = parts[1];
            
            // Read headers
            int contentLength = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            
            // Read body if present
            String body = null;
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                in.read(bodyChars, 0, contentLength);
                body = new String(bodyChars);
            }
            
            // Route the request
            handleRoute(method, uri, body, out);
            
        } catch (Exception e) {
            System.err.println("Error handling request: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }
    
    private static void handleRoute(String method, String uri, String body, OutputStream out) throws IOException {
        // Route based on method and URI
        
        if ("POST".equals(method) && uri.startsWith("/api/boards")) {
            handleSaveBoard(body, out);
        } else if ("GET".equals(method) && uri.equals("/api/boards")) {
            handleListBoards(out);
        } else if ("GET".equals(method) && uri.startsWith("/api/boards/")) {
            String boardId = uri.substring("/api/boards/".length());
            handleLoadBoard(boardId, out);
        } else if ("DELETE".equals(method) && uri.startsWith("/api/boards/")) {
            String boardId = uri.substring("/api/boards/".length());
            handleDeleteBoard(boardId, out);
        } else if ("OPTIONS".equals(method)) {
            handleCORS(out);
        } else {
            sendResponse(out, 404, "Not Found", "Endpoint not found: " + uri);
        }
    }
    
    private static void handleSaveBoard(String body, OutputStream out) {
        try {
            if (body == null || body.isEmpty()) {
                sendResponse(out, 400, "Bad Request", "Missing request body");
                return;
            }
            
            Board board = gson.fromJson(body, Board.class);
            
            if (board.getId() == null || board.getId().isEmpty()) {
                sendResponse(out, 400, "Bad Request", "Board ID is required");
                return;
            }
            
            // Save board and wait for completion (demonstrates async NIO but waits for result)
            try {
                storageService.saveBoard(board).get(); // Wait for completion
                
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Board saved successfully");
                response.addProperty("id", board.getId());
                
                sendJSONResponse(out, 200, "OK", response.toString());
            } catch (Exception e) {
                sendResponse(out, 500, "Internal Server Error", "Failed to save board: " + e.getMessage());
            }
            
        } catch (Exception e) {
            try {
                sendResponse(out, 400, "Bad Request", "Invalid board data: " + e.getMessage());
            } catch (IOException ioException) {
                System.err.println("Error sending error response: " + ioException.getMessage());
            }
        }
    }
    
    private static void handleLoadBoard(String boardId, OutputStream out) {
        try {
            // Load board and wait for completion (demonstrates async NIO but waits for result)
            Board board = storageService.loadBoard(boardId).get(); // Wait for completion
            String json = gson.toJson(board);
            sendJSONResponse(out, 200, "OK", json);
        } catch (Exception e) {
            try {
                sendResponse(out, 404, "Not Found", "Board not found: " + boardId);
            } catch (IOException ioException) {
                System.err.println("Error sending error response: " + ioException.getMessage());
            }
        }
    }
    
    private static void handleListBoards(OutputStream out) throws IOException {
        List<BoardStorageService.BoardMetadata> boards = storageService.listBoards();
        System.out.println("Listing boards: Found " + boards.size() + " boards");
        for (BoardStorageService.BoardMetadata board : boards) {
            System.out.println("  - " + board.getName() + " (" + board.getId() + ")");
        }
        String json = gson.toJson(boards);
        sendJSONResponse(out, 200, "OK", json);
    }
    
    private static void handleDeleteBoard(String boardId, OutputStream out) throws IOException {
        boolean success = storageService.deleteBoard(boardId);
        
        if (success) {
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Board deleted successfully");
            sendJSONResponse(out, 200, "OK", response.toString());
        } else {
            sendResponse(out, 404, "Not Found", "Board not found: " + boardId);
        }
    }
    
    private static void handleCORS(OutputStream out) throws IOException {
        String response = "HTTP/1.1 204 No Content\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n" +
                         "Access-Control-Allow-Headers: Content-Type\r\n" +
                         "\r\n";
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    
    private static void sendJSONResponse(OutputStream out, int statusCode, String statusMessage, String jsonBody) throws IOException {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        
        String response = "HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n" +
                         "Content-Type: application/json\r\n" +
                         "Content-Length: " + bodyBytes.length + "\r\n" +
                         "Access-Control-Allow-Origin: *\r\n" +
                         "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n" +
                         "Access-Control-Allow-Headers: Content-Type\r\n" +
                         "\r\n";
        
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }
    
    private static void sendResponse(OutputStream out, int statusCode, String statusMessage, String message) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("error", message);
        sendJSONResponse(out, statusCode, statusMessage, json.toString());
    }
    
    public static void stopServer() {
        running = false;
        threadPool.shutdown();
    }
}
