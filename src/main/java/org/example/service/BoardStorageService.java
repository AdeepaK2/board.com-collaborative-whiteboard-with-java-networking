package org.example.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import org.example.model.Room;
import org.example.model.ShapeData;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Service for persisting and loading whiteboard states using NIO (Non-blocking I/O)
 * 
 * NETWORK PROGRAMMING PRINCIPLE: Java NIO (Non-blocking I/O)
 * - Uses AsynchronousFileChannel for non-blocking file operations
 * - CompletionHandler callbacks for async read/write completion
 * - Allows server to handle other requests while file I/O is in progress
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
     * Save a board state using NIO async file I/O
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
            boardData.eraserStrokes = new ArrayList<>(); // Empty for now
            boardData.savedBy = username;
            boardData.savedAt = timestamp;
            boardData.shapeCount = boardData.shapes.size();
            
            // Save to file using async NIO
            String filename = boardId + ".json";
            Path filepath = Paths.get(BOARDS_DIR, filename);
            String json = gson.toJson(boardData);
            
            // Non-blocking async write - wait for completion
            writeFileAsync(filepath, json).get();
            
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
        } catch (InterruptedException | ExecutionException e) {
            return new SaveResult(false, null, "Failed to save board: " + e.getMessage());
        }
    }
    
    /**
     * Save a board state with shapes and strokes from JSON using NIO async I/O
     */
    public static SaveResult saveBoard(String boardName, JsonArray shapesJson, JsonArray strokesJson, JsonArray eraserStrokesJson, String username) {
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
            
            // Parse eraser strokes
            boardData.eraserStrokes = eraserStrokesJson != null ? gson.fromJson(eraserStrokesJson, strokesType) : new ArrayList<>();
            
            boardData.savedBy = username;
            boardData.savedAt = timestamp;
            boardData.shapeCount = boardData.shapes.size();
            
            // Save to file using async NIO
            String filename = boardId + ".json";
            Path filepath = Paths.get(BOARDS_DIR, filename);
            String json = gson.toJson(boardData);
            
            // Non-blocking async write - wait for completion
            writeFileAsync(filepath, json).get();
            
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
        } catch (InterruptedException | ExecutionException e) {
            return new SaveResult(false, null, "Failed to save board: " + e.getMessage());
        }
    }
    
    /**
     * Load a board state using NIO async file I/O
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
        
        try {
            // Non-blocking async read - wait for completion
            String json = readFileAsync(filepath).get();
            return gson.fromJson(json, BoardData.class);
        } catch (InterruptedException | ExecutionException e) {
            throw new IOException("Failed to load board: " + e.getMessage(), e);
        }
    }
    
    /**
     * List all saved boards
     */
    public static List<BoardMetadata> listBoards() {
        return new ArrayList<>(boardRegistry.values());
    }
    
    /**
     * Delete a saved board (only if user is the creator)
     */
    public static DeleteResult deleteBoard(String boardId, String username) {
        try {
            BoardMetadata metadata = boardRegistry.get(boardId);
            if (metadata == null) {
                return new DeleteResult(false, "Board not found");
            }
            
            // Check if user is authorized to delete (must be the creator)
            if (!metadata.savedBy.equals(username)) {
                return new DeleteResult(false, "You are not authorized to delete this board. Only the creator (" + metadata.savedBy + ") can delete it.");
            }
            
            Path filepath = Paths.get(BOARDS_DIR, metadata.filename);
            Files.deleteIfExists(filepath);
            boardRegistry.remove(boardId);
            saveBoardRegistry();
            
            return new DeleteResult(true, "Board deleted successfully");
        } catch (IOException e) {
            System.err.println("Failed to delete board: " + e.getMessage());
            return new DeleteResult(false, "Failed to delete board: " + e.getMessage());
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
     * Import board from JSON string using NIO async I/O
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
            
            // Non-blocking async write
            writeFileAsync(filepath, json).get();
            
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
        } catch (InterruptedException | ExecutionException e) {
            return new SaveResult(false, null, "Failed to import board: " + e.getMessage());
        }
    }
    
    // Helper methods
    
    private static String generateBoardId() {
        return "board-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Asynchronously write data to file using NIO
     * 
     * NIO PRINCIPLE: Non-blocking asynchronous file write
     * - Uses AsynchronousFileChannel instead of blocking Files.writeString()
     * - CompletionHandler callback executes when write completes
     * - Thread can continue processing other requests during I/O
     */
    private static CompletableFuture<Void> writeFileAsync(Path filepath, String content) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            // Open asynchronous file channel for writing
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                filepath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
            
            ByteBuffer buffer = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            
            // Asynchronous write with callback handler
            channel.write(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    try {
                        channel.close();
                        future.complete(null);
                        System.out.println("✓ Async write completed: " + filepath.getFileName());
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                }
                
                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        System.err.println("Error closing write channel: " + e.getMessage());
                    }
                    future.completeExceptionally(exc);
                }
            });
            
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Asynchronously read data from file using NIO
     * 
     * NIO PRINCIPLE: Non-blocking asynchronous file read
     * - Uses AsynchronousFileChannel for non-blocking reads
     * - Returns CompletableFuture that resolves when read completes
     * - Allows concurrent file reads without thread blocking
     */
    private static CompletableFuture<String> readFileAsync(Path filepath) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            // Open asynchronous file channel for reading
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                filepath,
                StandardOpenOption.READ
            );
            
            long fileSize = Files.size(filepath);
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            
            // Asynchronous read with callback handler
            channel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    try {
                        channel.close();
                        attachment.flip();
                        String content = StandardCharsets.UTF_8.decode(attachment).toString();
                        future.complete(content);
                        System.out.println("✓ Async read completed: " + filepath.getFileName());
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                }
                
                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        System.err.println("Error closing read channel: " + e.getMessage());
                    }
                    future.completeExceptionally(exc);
                }
            });
            
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        
        return future;
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
        public List<StrokeData> eraserStrokes;
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
    
    public static class DeleteResult {
        public boolean success;
        public String message;
        
        public DeleteResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
