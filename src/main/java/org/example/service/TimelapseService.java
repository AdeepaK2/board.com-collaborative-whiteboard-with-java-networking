package org.example.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.example.model.ShapeData;
import org.example.service.BoardStorageService.BoardData;
import org.example.service.TimelapseJobManager.TimelapseJob;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;

/**
 * Service for generating timelapse videos from whiteboard drawing history
 * 
 * NETWORK PROGRAMMING PRINCIPLES DEMONSTRATED:
 * - Asynchronous processing: Video generation runs in background thread
 * - Job queue pattern: Multiple timelapse requests handled concurrently
 * - Large binary file generation: Creates MP4 video files (10-50MB)
 * - Progress tracking: Updates status for client polling
 */
public class TimelapseService {
    
    private static final String TIMELAPSE_DIR = "saved_boards/timelapses";
    private static final int VIDEO_WIDTH = 1920;
    private static final int VIDEO_HEIGHT = 1080;
    private static final int FRAME_RATE = 30;
    private static final int DEFAULT_DURATION_SECONDS = 10;
    
    // Thread pool for async video generation
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    
    static {
        try {
            Files.createDirectories(Paths.get(TIMELAPSE_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create timelapse directory: " + e.getMessage());
        }
    }
    
    /**
     * Generate timelapse video asynchronously
     * 
     * NETWORK PRINCIPLE: Asynchronous processing with job tracking
     * Returns immediately with job ID, client polls for status
     */
    public static TimelapseJob generateTimelapseAsync(String boardId, int durationSeconds) {
        TimelapseJob job = TimelapseJobManager.createJob(boardId);
        
        // Submit to thread pool for background processing
        executor.submit(() -> {
            try {
                generateTimelapse(job, boardId, durationSeconds);
            } catch (Exception e) {
                TimelapseJobManager.markFailed(job.jobId, e.getMessage());
            }
        });
        
        return job;
    }
    
    /**
     * Generate timelapse video (runs in background thread)
     */
    private static void generateTimelapse(TimelapseJob job, String boardId, int durationSeconds) {
        TimelapseJobManager.markProcessing(job.jobId);
        
        try {
            // Load board data
            TimelapseJobManager.updateProgress(job.jobId, 10, "Loading board data...");
            BoardData boardData = BoardStorageService.loadBoard(boardId);
            
            // Calculate frame distribution
            int totalFrames = durationSeconds * FRAME_RATE;
            int shapesPerFrame = Math.max(1, boardData.shapes.size() / totalFrames);
            
            // Setup video encoder
            TimelapseJobManager.updateProgress(job.jobId, 20, "Initializing video encoder...");
            String videoFilename = job.jobId + ".mp4";
            Path videoPath = Paths.get(TIMELAPSE_DIR, videoFilename);
            
            SeekableByteChannel channel = NIOUtils.writableFileChannel(videoPath.toString());
            AWTSequenceEncoder encoder = new AWTSequenceEncoder(channel, Rational.R(FRAME_RATE, 1));
            
            // Generate frames
            TimelapseJobManager.updateProgress(job.jobId, 30, "Rendering frames...");
            
            for (int frameNum = 0; frameNum < totalFrames; frameNum++) {
                // Calculate which shapes to include in this frame
                int shapesToShow = Math.min((frameNum + 1) * shapesPerFrame, boardData.shapes.size());
                
                // Render frame
                BufferedImage frame = renderFrame(boardData.shapes, shapesToShow);
                encoder.encodeImage(frame);
                
                // Update progress (30% to 90%)
                int progress = 30 + (int)((frameNum / (double)totalFrames) * 60);
                if (frameNum % 10 == 0) {
                    TimelapseJobManager.updateProgress(job.jobId, progress, 
                        String.format("Rendering frame %d/%d", frameNum + 1, totalFrames));
                }
            }
            
            // Finalize video
            TimelapseJobManager.updateProgress(job.jobId, 95, "Finalizing video...");
            encoder.finish();
            channel.close();
            
            // Mark as completed
            TimelapseJobManager.markCompleted(job.jobId, videoPath.toString());
            
        } catch (IOException e) {
            TimelapseJobManager.markFailed(job.jobId, "Video generation failed: " + e.getMessage());
        }
    }
    
    /**
     * Render a single frame showing the drawing up to a certain point
     */
    private static BufferedImage renderFrame(List<ShapeData> allShapes, int shapesToShow) {
        BufferedImage image = new BufferedImage(VIDEO_WIDTH, VIDEO_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // Enable anti-aliasing for smoother shapes
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // White background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
        
        // Draw shapes in order (timelapse effect)
        for (int i = 0; i < shapesToShow && i < allShapes.size(); i++) {
            renderShape(g2d, allShapes.get(i));
        }
        
        g2d.dispose();
        return image;
    }
    
    /**
     * Render a single shape to the graphics context
     */
    private static void renderShape(Graphics2D g2d, ShapeData shape) {
        // Parse color
        Color color = parseColor(shape.getColor());
        g2d.setColor(color);
        
        // Set stroke width
        g2d.setStroke(new BasicStroke(shape.getSize()));
        
        int x = (int)shape.getX();
        int y = (int)shape.getY();
        int width = shape.getWidth() != null ? shape.getWidth().intValue() : 0;
        int height = shape.getHeight() != null ? shape.getHeight().intValue() : 0;
        
        // Draw based on shape type
        switch (shape.getType()) {
            case "rectangle":
                if (shape.getFillColor() != null && !shape.getFillColor().equals("transparent")) {
                    g2d.setColor(parseColor(shape.getFillColor()));
                    g2d.fillRect(x, y, width, height);
                    g2d.setColor(color);
                }
                g2d.drawRect(x, y, width, height);
                break;
                
            case "circle":
                if (shape.getFillColor() != null && !shape.getFillColor().equals("transparent")) {
                    g2d.setColor(parseColor(shape.getFillColor()));
                    g2d.fillOval(x, y, width, height);
                    g2d.setColor(color);
                }
                g2d.drawOval(x, y, width, height);
                break;
                
            case "line":
                int endX = shape.getEndX() != null ? shape.getEndX().intValue() : x + width;
                int endY = shape.getEndY() != null ? shape.getEndY().intValue() : y + height;
                g2d.drawLine(x, y, endX, endY);
                break;
                
            case "triangle":
                int[] xPoints = {x + width/2, x, x + width};
                int[] yPoints = {y, y + height, y + height};
                if (shape.getFillColor() != null && !shape.getFillColor().equals("transparent")) {
                    g2d.setColor(parseColor(shape.getFillColor()));
                    g2d.fillPolygon(xPoints, yPoints, 3);
                    g2d.setColor(color);
                }
                g2d.drawPolygon(xPoints, yPoints, 3);
                break;
                
            case "text":
                if (shape.getText() != null) {
                    int fontSize = shape.getFontSize() != null ? shape.getFontSize() : 16;
                    g2d.setFont(new Font("Arial", Font.PLAIN, fontSize));
                    g2d.drawString(shape.getText(), x, y);
                }
                break;
        }
    }
    
    /**
     * Parse hex color string to Color object
     */
    private static Color parseColor(String hexColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return Color.BLACK;
        }
        try {
            if (hexColor.startsWith("#")) {
                hexColor = hexColor.substring(1);
            }
            return new Color(Integer.parseInt(hexColor, 16));
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }
    
    /**
     * Get video file path for a completed job
     */
    public static Path getVideoPath(String jobId) {
        return Paths.get(TIMELAPSE_DIR, jobId + ".mp4");
    }
    
    /**
     * Shutdown executor (call on server shutdown)
     */
    public static void shutdown() {
        executor.shutdown();
    }
}
