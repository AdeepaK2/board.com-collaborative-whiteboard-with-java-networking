package org.example.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.example.service.UserDatabaseService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP API handler for authentication (register/login)
 * Provides REST endpoints for user authentication
 */
public class AuthApiHandler implements HttpHandler {
    private static final Gson gson = new Gson();
    private final UserDatabaseService userDB;
    
    public AuthApiHandler(UserDatabaseService userDB) {
        this.userDB = userDB;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Enable CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        try {
            if (path.equals("/api/auth/register") && "POST".equals(method)) {
                handleRegister(exchange);
            } else if (path.equals("/api/auth/login") && "POST".equals(method)) {
                handleLogin(exchange);
            } else if (path.equals("/api/auth/check") && "POST".equals(method)) {
                handleCheckUser(exchange);
            } else {
                sendResponse(exchange, 404, createErrorResponse("Endpoint not found"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }
    
    /**
     * POST /api/auth/register
     * Body: { "username": "...", "password": "..." }
     * Response: { "success": true/false, "message": "..." }
     */
    private void handleRegister(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        JsonObject request = gson.fromJson(body, JsonObject.class);
        
        if (!request.has("username") || !request.has("password")) {
            sendResponse(exchange, 400, createErrorResponse("Username and password are required"));
            return;
        }
        
        String username = request.get("username").getAsString();
        String password = request.get("password").getAsString();
        
        boolean success = userDB.registerUser(username, password);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        
        if (success) {
            response.put("message", "Registration successful");
            sendResponse(exchange, 201, gson.toJson(response)); // 201 Created
        } else {
            response.put("message", "Username already exists");
            sendResponse(exchange, 409, gson.toJson(response)); // 409 Conflict
        }
    }
    
    /**
     * POST /api/auth/login
     * Body: { "username": "...", "password": "..." }
     * Response: { "success": true/false, "message": "...", "username": "..." }
     */
    private void handleLogin(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        JsonObject request = gson.fromJson(body, JsonObject.class);
        
        if (!request.has("username") || !request.has("password")) {
            sendResponse(exchange, 400, createErrorResponse("Username and password are required"));
            return;
        }
        
        String username = request.get("username").getAsString();
        String password = request.get("password").getAsString();
        
        boolean success = userDB.loginUser(username, password);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        
        if (success) {
            response.put("message", "Login successful");
            response.put("username", username);
            sendResponse(exchange, 200, gson.toJson(response));
        } else {
            response.put("message", "Invalid username or password");
            sendResponse(exchange, 401, gson.toJson(response)); // 401 Unauthorized
        }
    }
    
    /**
     * POST /api/auth/check
     * Body: { "username": "..." }
     * Response: { "exists": true/false }
     */
    private void handleCheckUser(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        JsonObject request = gson.fromJson(body, JsonObject.class);
        
        if (!request.has("username")) {
            sendResponse(exchange, 400, createErrorResponse("Username is required"));
            return;
        }
        
        String username = request.get("username").getAsString();
        boolean exists = userDB.userExists(username);
        
        Map<String, Object> response = new HashMap<>();
        response.put("exists", exists);
        
        sendResponse(exchange, 200, gson.toJson(response));
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
