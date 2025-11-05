package org.example.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.model.Room;
import org.example.model.ShapeData;
import org.example.server.modules.RoomManager;
import org.example.service.BoardStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP API handler for board save/load operations
 */
public class BoardApiHandler implements HttpHandler {
    private static final Gson gson = new Gson();
    private final RoomManager roomManager;
    
    public BoardApiHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Enable CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        try {
            if (path.equals("/api/boards/save") && "POST".equals(method)) {
                handleSaveBoard(exchange);
            } else if (path.equals("/api/boards/list") && "GET".equals(method)) {
                handleListBoards(exchange);
            } else if (path.startsWith("/api/boards/load/") && "GET".equals(method)) {
                handleLoadBoard(exchange);
            } else if (path.startsWith("/api/boards/delete/") && "DELETE".equals(method)) {
                handleDeleteBoard(exchange);
            } else if (path.equals("/api/boards/export") && "POST".equals(method)) {
                handleExportBoard(exchange);
            } else if (path.equals("/api/boards/import") && "POST".equals(method)) {
                handleImportBoard(exchange);
            } else {
                sendResponse(exchange, 404, createErrorResponse("Endpoint not found"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }
    
    private void handleSaveBoard(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        JsonObject request = gson.fromJson(body, JsonObject.class);
        
        String boardName = request.get("boardName").getAsString();
        String roomId = request.get("roomId").getAsString();
        String username = request.get("username").getAsString();
        
        Room room = roomManager.getRoom(roomId);
        if (room == null) {
            sendResponse(exchange, 404, createErrorResponse("Room not found"));
            return;
        }
        
        BoardStorageService.SaveResult result = BoardStorageService.saveBoard(boardName, room, username);
        
        if (result.success) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("boardId", result.boardId);
            response.put("message", result.message);
            sendResponse(exchange, 200, gson.toJson(response));
        } else {
            sendResponse(exchange, 500, createErrorResponse(result.message));
        }
    }
    
    private void handleListBoards(HttpExchange exchange) throws IOException {
        List<BoardStorageService.BoardMetadata> boards = BoardStorageService.listBoards();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("boards", boards);
        
        sendResponse(exchange, 200, gson.toJson(response));
    }
    
    private void handleLoadBoard(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String boardId = path.substring("/api/boards/load/".length());
        
        try {
            BoardStorageService.BoardData boardData = BoardStorageService.loadBoard(boardId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("board", boardData);
            
            sendResponse(exchange, 200, gson.toJson(response));
        } catch (IOException e) {
            sendResponse(exchange, 404, createErrorResponse(e.getMessage()));
        }
    }
    
    private void handleDeleteBoard(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String boardId = path.substring("/api/boards/delete/".length());
        
        boolean success = BoardStorageService.deleteBoard(boardId);
        
        if (success) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Board deleted successfully");
            sendResponse(exchange, 200, gson.toJson(response));
        } else {
            sendResponse(exchange, 404, createErrorResponse("Board not found or failed to delete"));
        }
    }
    
    private void handleExportBoard(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        JsonObject request = gson.fromJson(body, JsonObject.class);
        String boardId = request.get("boardId").getAsString();
        
        try {
            String jsonData = BoardStorageService.exportBoard(boardId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", jsonData);
            
            sendResponse(exchange, 200, gson.toJson(response));
        } catch (IOException e) {
            sendResponse(exchange, 404, createErrorResponse(e.getMessage()));
        }
    }
    
    private void handleImportBoard(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        JsonObject request = gson.fromJson(body, JsonObject.class);
        
        String boardName = request.get("boardName").getAsString();
        String jsonData = request.get("data").getAsString();
        String username = request.get("username").getAsString();
        
        BoardStorageService.SaveResult result = BoardStorageService.importBoard(boardName, jsonData, username);
        
        if (result.success) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("boardId", result.boardId);
            response.put("message", result.message);
            sendResponse(exchange, 200, gson.toJson(response));
        } else {
            sendResponse(exchange, 500, createErrorResponse(result.message));
        }
    }
    
    // Helper methods
    
    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
    
    private String createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        return gson.toJson(error);
    }
}
