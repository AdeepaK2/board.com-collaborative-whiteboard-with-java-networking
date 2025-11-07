package org.example.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import org.example.model.Room;
import org.example.model.ShapeData;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for persisting and loading whiteboard states
 */
public class BoardStorageService {
    private static final String BOARDS_DIR = "saved_boards";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, BoardMetadata> boardRegistry = new ConcurrentHashMap<>();
    
    static {
        // Ensure boards directory exists
        try {
            Files.createDirectories(Paths.get(BOARDS_DIR));
            loadBoardRegistry();
        } catch (IOException e) {
            System.err.println("Failed to create boards directory: " + e.getMessage());
        }
    }
    
    /**
     * Save a board state
     */
    public static SaveResult saveBoard(String boardName, Room room, String username) {
        try {
            String boardId = generateBoardId();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            BoardData boardData = new BoardData();
            boardData.boardId = boardId;
            boardData.boardName = boardName;
            boardData.roomId = room.getRoomId();
            boardData.shapes = new ArrayList<>(room.getShapes().values());
            boardData.strokes = new ArrayList<>(); // Empty for now
            boardData.savedBy = username;
            boardData.savedAt = timestamp;
            boardData.shapeCount = boardData.shapes.size();
            
            // Save to file
            String filename = boardId + ".json";
            Path filepath = Paths.get(BOARDS_DIR, filename);
            String json = gson.toJson(boardData);
            Files.writeString(filepath, json);
            
            // Update registry
            BoardMetadata metadata = new BoardMetadata();
            metadata.boardId = boardId;
            metadata.boardName = boardName;
            metadata.savedBy = username;
            metadata.savedAt = timestamp;
            metadata.shapeCount = boardData.shapeCount;
            metadata.filename = filename;
            
            boardRegistry.put(boardId, metadata);
            saveBoardRegistry();
            
            return new SaveResult(true, boardId, "Board saved successfully");
        } catch (IOException e) {
            return new SaveResult(false, null, "Failed to save board: " + e.getMessage());
        }
    }
    
    /**
     * Save a board state with shapes and strokes from JSON
     */
    public static SaveResult saveBoard(String boardName, JsonArray shapesJson, JsonArray strokesJson, String username) {
        try {
            String boardId = generateBoardId();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            BoardData boardData = new BoardData();
            boardData.boardId = boardId;
            boardData.boardName = boardName;
            boardData.roomId = "";
            
            // Parse shapes
            Type shapesType = new TypeToken<List<ShapeData>>(){}.getType();
            boardData.shapes = shapesJson != null ? gson.fromJson(shapesJson, shapesType) : new ArrayList<>();
            
            // Parse strokes
            Type strokesType = new TypeToken<List<StrokeData>>(){}.getType();
            boardData.strokes = strokesJson != null ? gson.fromJson(strokesJson, strokesType) : new ArrayList<>();
            
            boardData.savedBy = username;
            boardData.savedAt = timestamp;
            boardData.shapeCount = boardData.shapes.size();
            
            // Save to file
            String filename = boardId + ".json";
            Path filepath = Paths.get(BOARDS_DIR, filename);
            String json = gson.toJson(boardData);
            Files.writeString(filepath, json);
            
            // Update registry
            BoardMetadata metadata = new BoardMetadata();
            metadata.boardId = boardId;
            metadata.boardName = boardName;
            metadata.savedBy = username;
            metadata.savedAt = timestamp;
            metadata.shapeCount = boardData.shapeCount;
            metadata.filename = filename;
            
            boardRegistry.put(boardId, metadata);
            saveBoardRegistry();
            
            return new SaveResult(true, boardId, "Board saved successfully");
        } catch (Exception e) {
            return new SaveResult(false, null, "Failed to save board: " + e.getMessage());
        }
    }
    
    /**
     * Load a board state
     */
    public static BoardData loadBoard(String boardId) throws IOException {
        BoardMetadata metadata = boardRegistry.get(boardId);
        if (metadata == null) {
            throw new IOException("Board not found: " + boardId);
        }
        
        Path filepath = Paths.get(BOARDS_DIR, metadata.filename);
        if (!Files.exists(filepath)) {
            throw new IOException("Board file not found: " + metadata.filename);
        }
        
        String json = Files.readString(filepath);
        return gson.fromJson(json, BoardData.class);
    }
    
    /**
     * List all saved boards
     */
    public static List<BoardMetadata> listBoards() {
        return new ArrayList<>(boardRegistry.values());
    }
    
    /**
     * Delete a saved board
     */
    public static boolean deleteBoard(String boardId) {
        try {
            BoardMetadata metadata = boardRegistry.get(boardId);
            if (metadata == null) {
                return false;
            }
            
            Path filepath = Paths.get(BOARDS_DIR, metadata.filename);
            Files.deleteIfExists(filepath);
            boardRegistry.remove(boardId);
            saveBoardRegistry();
            
            return true;
        } catch (IOException e) {
            System.err.println("Failed to delete board: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Export board as JSON string
     */
    public static String exportBoard(String boardId) throws IOException {
        BoardData data = loadBoard(boardId);
        return gson.toJson(data);
    }
    
    /**
     * Import board from JSON string
     */
    public static SaveResult importBoard(String boardName, String jsonData, String username) {
        try {
            BoardData data = gson.fromJson(jsonData, BoardData.class);
            data.boardName = boardName;
            data.boardId = generateBoardId();
            data.savedBy = username;
            data.savedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            String filename = data.boardId + ".json";
            Path filepath = Paths.get(BOARDS_DIR, filename);
            String json = gson.toJson(data);
            Files.writeString(filepath, json);
            
            BoardMetadata metadata = new BoardMetadata();
            metadata.boardId = data.boardId;
            metadata.boardName = boardName;
            metadata.savedBy = username;
            metadata.savedAt = data.savedAt;
            metadata.shapeCount = data.shapes.size();
            metadata.filename = filename;
            
            boardRegistry.put(data.boardId, metadata);
            saveBoardRegistry();
            
            return new SaveResult(true, data.boardId, "Board imported successfully");
        } catch (Exception e) {
            return new SaveResult(false, null, "Failed to import board: " + e.getMessage());
        }
    }
    
    // Helper methods
    
    private static String generateBoardId() {
        return "board-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private static void loadBoardRegistry() {
        try {
            Path registryPath = Paths.get(BOARDS_DIR, "registry.json");
            if (Files.exists(registryPath)) {
                String json = Files.readString(registryPath);
                BoardMetadata[] metadataArray = gson.fromJson(json, BoardMetadata[].class);
                if (metadataArray != null) {
                    for (BoardMetadata metadata : metadataArray) {
                        boardRegistry.put(metadata.boardId, metadata);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load board registry: " + e.getMessage());
        }
    }
    
    private static void saveBoardRegistry() {
        try {
            Path registryPath = Paths.get(BOARDS_DIR, "registry.json");
            List<BoardMetadata> metadataList = new ArrayList<>(boardRegistry.values());
            String json = gson.toJson(metadataList);
            Files.writeString(registryPath, json);
        } catch (IOException e) {
            System.err.println("Failed to save board registry: " + e.getMessage());
        }
    }
    
    // Data classes
    
    public static class BoardData {
        public String boardId;
        public String boardName;
        public String roomId;
        public List<ShapeData> shapes;
        public List<StrokeData> strokes;
        public String savedBy;
        public String savedAt;
        public int shapeCount;
    }
    
    public static class StrokeData {
        public List<DrawPoint> points;
    }
    
    public static class DrawPoint {
        public double x;
        public double y;
        public String color;
        public double size;
    }
    
    public static class BoardMetadata {
        public String boardId;
        public String boardName;
        public String savedBy;
        public String savedAt;
        public int shapeCount;
        public String filename;
    }
    
    public static class SaveResult {
        public boolean success;
        public String boardId;
        public String message;
        
        public SaveResult(boolean success, String boardId, String message) {
            this.success = success;
            this.boardId = boardId;
            this.message = message;
        }
    }
}
