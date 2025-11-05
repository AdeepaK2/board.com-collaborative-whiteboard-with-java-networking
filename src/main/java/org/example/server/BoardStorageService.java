package org.example.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.model.Board;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for storing and retrieving boards using Java NIO
 * Demonstrates non-blocking file I/O operations
 */
public class BoardStorageService {
    private static final String STORAGE_DIR = "saved_boards";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public BoardStorageService() {
        initializeStorage();
    }
    
    /**
     * Initialize storage directory
     */
    private void initializeStorage() {
        try {
            Path storagePath = Paths.get(STORAGE_DIR);
            if (!Files.exists(storagePath)) {
                Files.createDirectories(storagePath);
                System.out.println("Created storage directory: " + STORAGE_DIR);
            }
        } catch (IOException e) {
            System.err.println("Error creating storage directory: " + e.getMessage());
        }
    }
    
    /**
     * Save board to file using NIO AsynchronousFileChannel
     * @param board The board to save
     * @return CompletableFuture that completes when save is done
     */
    public CompletableFuture<Void> saveBoard(Board board) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            board.setLastModified(System.currentTimeMillis());
            String json = gson.toJson(board);
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            
            Path filePath = Paths.get(STORAGE_DIR, board.getId() + ".json");
            
            // Use AsynchronousFileChannel for non-blocking I/O
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(
                filePath,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
            
            ByteBuffer buffer = ByteBuffer.wrap(data);
            
            // Write asynchronously with completion handler
            fileChannel.write(buffer, 0, null, new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(Integer result, Object attachment) {
                    try {
                        fileChannel.close();
                        System.out.println("Board saved successfully: " + board.getId());
                        future.complete(null);
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                }
                
                @Override
                public void failed(Throwable exc, Object attachment) {
                    try {
                        fileChannel.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                    System.err.println("Failed to save board: " + exc.getMessage());
                    future.completeExceptionally(exc);
                }
            });
            
        } catch (Exception e) {
            System.err.println("Error saving board: " + e.getMessage());
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Load board from file using NIO AsynchronousFileChannel
     * @param boardId The ID of the board to load
     * @return CompletableFuture containing the board
     */
    public CompletableFuture<Board> loadBoard(String boardId) {
        CompletableFuture<Board> future = new CompletableFuture<>();
        
        try {
            Path filePath = Paths.get(STORAGE_DIR, boardId + ".json");
            
            if (!Files.exists(filePath)) {
                future.completeExceptionally(new IOException("Board not found: " + boardId));
                return future;
            }
            
            // Use AsynchronousFileChannel for non-blocking I/O
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(
                filePath,
                StandardOpenOption.READ
            );
            
            long fileSize = Files.size(filePath);
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            
            // Read asynchronously with completion handler
            fileChannel.read(buffer, 0, null, new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(Integer result, Object attachment) {
                    try {
                        fileChannel.close();
                        buffer.flip();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        String json = new String(data, StandardCharsets.UTF_8);
                        Board board = gson.fromJson(json, Board.class);
                        System.out.println("Board loaded successfully: " + boardId);
                        future.complete(board);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
                
                @Override
                public void failed(Throwable exc, Object attachment) {
                    try {
                        fileChannel.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                    System.err.println("Failed to load board: " + exc.getMessage());
                    future.completeExceptionally(exc);
                }
            });
            
        } catch (Exception e) {
            System.err.println("Error loading board: " + e.getMessage());
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * List all saved boards
     * @return List of board metadata
     */
    public List<BoardMetadata> listBoards() {
        List<BoardMetadata> boards = new ArrayList<>();
        
        try {
            Path storagePath = Paths.get(STORAGE_DIR);
            
            if (!Files.exists(storagePath)) {
                return boards;
            }
            
            // Use NIO Files.walk for efficient directory traversal
            try (Stream<Path> paths = Files.walk(storagePath, 1)) {
                boards = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(this::loadBoardMetadata)
                    .filter(m -> m != null)
                    .collect(Collectors.toList());
            }
            
        } catch (IOException e) {
            System.err.println("Error listing boards: " + e.getMessage());
        }
        
        return boards;
    }
    
    /**
     * Load only metadata from a board file (without full content)
     */
    private BoardMetadata loadBoardMetadata(Path filePath) {
        try {
            // Read file using NIO
            String json = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            Board board = gson.fromJson(json, Board.class);
            
            return new BoardMetadata(
                board.getId(),
                board.getName(),
                board.getCreatedBy(),
                board.getCreatedAt(),
                board.getLastModified(),
                board.getStrokes().size() + board.getShapes().size()
            );
        } catch (Exception e) {
            System.err.println("Error loading metadata from " + filePath + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Delete a board
     */
    public boolean deleteBoard(String boardId) {
        try {
            Path filePath = Paths.get(STORAGE_DIR, boardId + ".json");
            Files.deleteIfExists(filePath);
            System.out.println("Board deleted: " + boardId);
            return true;
        } catch (IOException e) {
            System.err.println("Error deleting board: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Lightweight board metadata for listing
     */
    public static class BoardMetadata {
        private final String id;
        private final String name;
        private final String createdBy;
        private final long createdAt;
        private final long lastModified;
        private final int elementCount;
        
        public BoardMetadata(String id, String name, String createdBy, long createdAt, long lastModified, int elementCount) {
            this.id = id;
            this.name = name;
            this.createdBy = createdBy;
            this.createdAt = createdAt;
            this.lastModified = lastModified;
            this.elementCount = elementCount;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getCreatedBy() { return createdBy; }
        public long getCreatedAt() { return createdAt; }
        public long getLastModified() { return lastModified; }
        public int getElementCount() { return elementCount; }
    }
}
