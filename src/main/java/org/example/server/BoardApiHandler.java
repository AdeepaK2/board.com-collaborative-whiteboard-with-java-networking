package org.example.server;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.example.model.Room;
import org.example.server.modules.RoomManager;
import org.example.service.BoardStorageService;
import org.example.service.TimelapseJobManager;
import org.example.service.TimelapseService;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

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
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        // Note: Since the handler is registered for "/api/boards", the path is relative to that context
        // So "/api/boards/uploadImage" becomes "/uploadImage" here
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        
        // Remove the "/api/boards" prefix if present (for backwards compatibility)
        if (path.startsWith("/api/boards")) {
            path = path.substring("/api/boards".length());
        }
        
        // Ensure path starts with "/"
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        try {
            if (path.equals("/save") && "POST".equals(method)) {
                handleSaveBoard(exchange);
            } else if (path.equals("/list") && "GET".equals(method)) {
                handleListBoards(exchange);
            } else if (path.startsWith("/load/") && "GET".equals(method)) {
                handleLoadBoard(exchange);
            } else if (path.startsWith("/delete/") && "DELETE".equals(method)) {
                handleDeleteBoard(exchange);
            } else if (path.equals("/export") && "POST".equals(method)) {
                handleExportBoard(exchange);
            } else if (path.equals("/import") && "POST".equals(method)) {
                handleImportBoard(exchange);
            } else if (path.equals("/generate-timelapse") && "POST".equals(method)) {
                handleGenerateTimelapse(exchange);
            } else if (path.startsWith("/timelapse-status/") && "GET".equals(method)) {
                handleTimelapseStatus(exchange);
            } else if (path.startsWith("/timelapse-video/") && "GET".equals(method)) {
                handleDownloadTimelapse(exchange);
            } else if (path.equals("/uploadImage") && "POST".equals(method)) {
                handleUploadImage(exchange);
            } else {
                sendResponse(exchange, 404, createErrorResponse("Endpoint not found: " + path));
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
        
        // Get shapes, strokes, and eraserStrokes from request if provided, otherwise fall back to room data
        JsonArray shapesJson = request.has("shapes") ? request.getAsJsonArray("shapes") : null;
        JsonArray strokesJson = request.has("strokes") ? request.getAsJsonArray("strokes") : null;
        JsonArray eraserStrokesJson = request.has("eraserStrokes") ? request.getAsJsonArray("eraserStrokes") : null;
        
        BoardStorageService.SaveResult result;
        
        if (shapesJson != null || strokesJson != null || eraserStrokesJson != null) {
            // Save with provided shapes, strokes, and eraserStrokes
            result = BoardStorageService.saveBoard(boardName, shapesJson, strokesJson, eraserStrokesJson, username);
        } else {
            // Fallback to room data
            Room room = roomManager.getRoom(roomId);
            if (room == null) {
                sendResponse(exchange, 404, createErrorResponse("Room not found"));
                return;
            }
            result = BoardStorageService.saveBoard(boardName, room, username);
        }
        
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
        // Remove "/api/boards" prefix if present
        if (path.startsWith("/api/boards")) {
            path = path.substring("/api/boards".length());
        }
        String boardId = path.substring("/load/".length());
        
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
        // Remove "/api/boards" prefix if present
        if (path.startsWith("/api/boards")) {
            path = path.substring("/api/boards".length());
        }
        String boardId = path.substring("/delete/".length());
        
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
    
    /**
     * Generate timelapse video (async job)
     * 
     * NETWORK PRINCIPLE: HTTP 202 Accepted - Asynchronous processing
     * Returns job ID immediately, client polls for completion
     */
    private void handleGenerateTimelapse(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        JsonObject request = gson.fromJson(body, JsonObject.class);
        
        String boardId = request.get("boardId").getAsString();
        int duration = request.has("duration") ? request.get("duration").getAsInt() : 10;
        
        // Start async video generation
        TimelapseJobManager.TimelapseJob job = TimelapseService.generateTimelapseAsync(boardId, duration);
        
        // Return 202 Accepted with job ID
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("jobId", job.jobId);
        response.put("status", job.status.toString());
        response.put("message", "Timelapse generation started");
        
        sendResponse(exchange, 202, gson.toJson(response));
    }
    
    /**
     * Get timelapse job status
     * 
     * NETWORK PRINCIPLE: Polling pattern for async job status
     */
    private void handleTimelapseStatus(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        // Remove "/api/boards" prefix if present
        if (path.startsWith("/api/boards")) {
            path = path.substring("/api/boards".length());
        }
        String jobId = path.substring("/timelapse-status/".length());
        
        TimelapseJobManager.TimelapseJob job = TimelapseJobManager.getJob(jobId);
        
        if (job == null) {
            sendResponse(exchange, 404, createErrorResponse("Job not found"));
            return;
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("jobId", job.jobId);
        response.put("status", job.status.toString());
        response.put("progress", job.progress);
        response.put("message", job.message);
        
        if (job.status == TimelapseJobManager.JobStatus.COMPLETED) {
            response.put("videoUrl", "/api/boards/timelapse-video/" + job.jobId);
        }
        
        sendResponse(exchange, 200, gson.toJson(response));
    }
    
    /**
     * Download timelapse video
     * 
     * NETWORK PRINCIPLES:
     * - Binary data transfer (MP4 video)
     * - Large file streaming
     * - Content-Type: video/mp4
     * - Content-Disposition for download
     */
    private void handleDownloadTimelapse(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        // Remove "/api/boards" prefix if present
        if (path.startsWith("/api/boards")) {
            path = path.substring("/api/boards".length());
        }
        String jobId = path.substring("/timelapse-video/".length());
        
        TimelapseJobManager.TimelapseJob job = TimelapseJobManager.getJob(jobId);
        
        if (job == null || job.status != TimelapseJobManager.JobStatus.COMPLETED) {
            sendResponse(exchange, 404, createErrorResponse("Video not ready or not found"));
            return;
        }
        
        java.nio.file.Path videoPath = TimelapseService.getVideoPath(jobId);
        
        if (!java.nio.file.Files.exists(videoPath)) {
            sendResponse(exchange, 404, createErrorResponse("Video file not found"));
            return;
        }
        
        // Read video file
        byte[] videoData = java.nio.file.Files.readAllBytes(videoPath);
        
        // Set video content type and headers
        exchange.getResponseHeaders().set("Content-Type", "video/mp4");
        exchange.getResponseHeaders().set("Content-Disposition", 
            "attachment; filename=\"timelapse-" + jobId + ".mp4\"");
        
        // Send binary video data
        exchange.sendResponseHeaders(200, videoData.length);
        OutputStream os = exchange.getResponseBody();
        os.write(videoData);
        os.close();
        
        System.out.println("📹 Sent timelapse video: " + jobId + " (" + videoData.length + " bytes)");
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
    
    /**
     * Handle image upload
     * POST /api/boards/uploadImage?room={roomName}
     * Body: multipart/form-data with image file
     */
    private void handleUploadImage(HttpExchange exchange) throws IOException {
        try {
            // Get room name from query parameter
            String query = exchange.getRequestURI().getQuery();
            String roomName = null;
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2 && "room".equals(keyValue[0])) {
                        roomName = java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        break;
                    }
                }
            }

            if (roomName == null || roomName.isEmpty()) {
                sendResponse(exchange, 400, createErrorResponse("Room name is required"));
                return;
            }

            // Find room by name
            Room targetRoom = null;
            for (Room room : roomManager.getAllRooms()) {
                if (roomName.equals(room.getRoomName())) {
                    targetRoom = room;
                    break;
                }
            }

            if (targetRoom == null) {
                sendResponse(exchange, 404, createErrorResponse("Room not found: " + roomName));
                return;
            }

            // Get content type
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                sendResponse(exchange, 400, createErrorResponse("Content-Type must be multipart/form-data"));
                return;
            }

            // Parse multipart form data
            String boundary = extractBoundary(contentType);
            if (boundary == null) {
                sendResponse(exchange, 400, createErrorResponse("Invalid multipart boundary"));
                return;
            }

            // Read and parse multipart data
            InputStream inputStream = exchange.getRequestBody();
            byte[] fileData = parseMultipartFormData(inputStream, boundary);
            
            if (fileData == null || fileData.length == 0) {
                sendResponse(exchange, 400, createErrorResponse("No image file found in request"));
                return;
            }

            // Generate unique filename
            String fileExtension = "png"; // Default to PNG
            String uniqueFilename = UUID.randomUUID().toString() + "." + fileExtension;
            
            // Ensure images directory exists
            Path imagesDir = Paths.get("saved_boards", "images");
            Files.createDirectories(imagesDir);
            
            // Save file
            Path filePath = imagesDir.resolve(uniqueFilename);
            Files.write(filePath, fileData);
            
            System.out.println("📷 Image saved: " + uniqueFilename + " (" + fileData.length + " bytes)");

            // Get image dimensions
            int imageWidth = 200; // Default
            int imageHeight = 200; // Default
            try {
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(fileData));
                if (img != null) {
                    imageWidth = img.getWidth();
                    imageHeight = img.getHeight();
                    System.out.println("📐 Image dimensions: " + imageWidth + "x" + imageHeight);
                }
            } catch (Exception e) {
                System.err.println("⚠️ Could not read image dimensions: " + e.getMessage());
            }

            // Generate public URL (served from WebSocket server on port 8080)
            String imageUrl = "http://localhost:8080/images/" + uniqueFilename;
            
            // Broadcast image to room via WebSocket with dimensions
            broadcastImageToRoom(targetRoom.getRoomId(), imageUrl, roomName, imageWidth, imageHeight);
            
            // Send success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("imageUrl", imageUrl);
            response.put("filename", uniqueFilename);
            response.put("message", "Image uploaded successfully");
            
            sendResponse(exchange, 200, gson.toJson(response));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, createErrorResponse("Failed to upload image: " + e.getMessage()));
        }
    }

    /**
     * Extract boundary from Content-Type header
     */
    private String extractBoundary(String contentType) {
        if (contentType == null) return null;
        String[] parts = contentType.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                return part.substring("boundary=".length()).trim();
            }
        }
        return null;
    }

    /**
     * Parse multipart/form-data and extract file content (handles binary data correctly)
     */
    private byte[] parseMultipartFormData(InputStream inputStream, String boundary) throws IOException {
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        byte[] endBoundaryBytes = ("--" + boundary + "--").getBytes(StandardCharsets.UTF_8);
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] doubleCrlf = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        
        // Read all data
        byte[] allData = inputStream.readAllBytes();
        
        // Find first boundary
        int boundaryPos = indexOf(allData, boundaryBytes, 0);
        if (boundaryPos == -1) {
            return null;
        }
        
        // Find header end (double CRLF)
        int headerEnd = indexOf(allData, doubleCrlf, boundaryPos);
        if (headerEnd == -1) {
            // Try single CRLF as fallback
            int singleCrlfPos = indexOf(allData, crlf, boundaryPos);
            if (singleCrlfPos == -1) {
                return null;
            }
            headerEnd = singleCrlfPos + crlf.length;
        } else {
            headerEnd += doubleCrlf.length;
        }
        
        // Find next boundary or end boundary
        int nextBoundaryPos = indexOf(allData, boundaryBytes, headerEnd);
        int endBoundaryPos = indexOf(allData, endBoundaryBytes, headerEnd);
        
        int fileEndPos;
        if (endBoundaryPos != -1 && (nextBoundaryPos == -1 || endBoundaryPos < nextBoundaryPos)) {
            fileEndPos = endBoundaryPos;
        } else if (nextBoundaryPos != -1) {
            fileEndPos = nextBoundaryPos;
        } else {
            // No boundary found, use end of data (shouldn't happen but handle gracefully)
            fileEndPos = allData.length;
        }
        
        // Skip trailing CRLF before boundary
        while (fileEndPos > headerEnd && 
               (allData[fileEndPos - 1] == '\n' || allData[fileEndPos - 1] == '\r')) {
            fileEndPos--;
        }
        
        // Extract file data
        int fileLength = fileEndPos - headerEnd;
        if (fileLength <= 0) {
            return null;
        }
        
        byte[] fileData = new byte[fileLength];
        System.arraycopy(allData, headerEnd, fileData, 0, fileLength);
        
        return fileData;
    }
    
    /**
     * Find index of byte array within another byte array
     */
    private int indexOf(byte[] haystack, byte[] needle, int fromIndex) {
        if (needle.length == 0) {
            return fromIndex;
        }
        if (fromIndex >= haystack.length) {
            return -1;
        }
        
        for (int i = fromIndex; i <= haystack.length - needle.length; i++) {
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Broadcast image URL to all clients in the room via WebSocket
     * Also stores the image shape in the room's drawing history for new users
     */
    private void broadcastImageToRoom(String roomId, String imageUrl, String roomName, 
                                     int imageWidth, int imageHeight) {
        try {
            JsonObject message = new JsonObject();
            message.addProperty("type", "shapeAdded");
            
            JsonObject payload = new JsonObject();
            payload.addProperty("shapeType", "IMAGE");
            payload.addProperty("url", imageUrl);
            payload.addProperty("room", roomName);
            payload.addProperty("x", 100); // Default position
            payload.addProperty("y", 100); // Default position
            payload.addProperty("width", imageWidth); // Actual image width
            payload.addProperty("height", imageHeight); // Actual image height
            
            message.add("payload", payload);
            
            String messageJson = message.toString();
            
            // Store in room's drawing history so new users can see it when they join
            Room room = roomManager.getRoom(roomId);
            if (room != null) {
                // Store the message in drawing history
                room.addToDrawingHistory(messageJson);
                
                // Also create a ShapeData object and store it in the room's shape map
                // Generate a shape ID for the image
                String shapeId = "img-" + UUID.randomUUID().toString();
                org.example.model.ShapeData shapeData = new org.example.model.ShapeData();
                shapeData.setId(shapeId);
                shapeData.setType("image");
                shapeData.setX(100);
                shapeData.setY(100);
                shapeData.setWidth((double) imageWidth);
                shapeData.setHeight((double) imageHeight);
                shapeData.setUrl(imageUrl); // Store image URL
                shapeData.setColor("#000000");
                shapeData.setSize(1);
                room.addShape(shapeId, shapeData);
            }
            
            // Broadcast via WebSocket server
            Server.broadcastToRoomPublic(roomId, messageJson);
            
            System.out.println("📤 Broadcasted image to room: " + roomName + " [" + roomId + "] (" + 
                             imageWidth + "x" + imageHeight + ")");
        } catch (Exception e) {
            System.err.println("❌ Failed to broadcast image: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        return gson.toJson(error);
    }
}
