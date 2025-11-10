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

    // Replay step used to build the timeline for timelapse rendering
    private static class Step {
        String kind; // "shape" or "stroke"
        int shapeIndex;
        int strokeIndex;
        int pointCount; // for stroke: number of points to reveal
    }
    
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
            
            // Build replay timeline: include shapes and incremental stroke points so the
            // video reflects the drawing process instead of snapping final shapes.
            TimelapseJobManager.updateProgress(job.jobId, 20, "Building replay timeline...");

            // Each step represents either a shape being added, or an incremental stroke point
            java.util.List<Step> steps = new java.util.ArrayList<>();

            // Add shapes as single steps (they typically represent discrete actions)
            if (boardData.shapes != null) {
                for (int i = 0; i < boardData.shapes.size(); i++) {
                    Step s = new Step(); s.kind = "shape"; s.shapeIndex = i; s.strokeIndex = -1; s.pointCount = 0;
                    steps.add(s);
                }
            }

            // Add strokes as granular steps: reveal stroke points one-by-one so pen drawings
            // are animated progressively. If strokes is null it's safe.
            if (boardData.strokes != null) {
                for (int si = 0; si < boardData.strokes.size(); si++) {
                    BoardStorageService.StrokeData sd = boardData.strokes.get(si);
                    if (sd == null || sd.points == null || sd.points.size() == 0) continue;
                    // First step represents the first point, then progressively add more
                    for (int p = 1; p <= sd.points.size(); p++) {
                        Step s = new Step(); s.kind = "stroke"; s.strokeIndex = si; s.shapeIndex = -1; s.pointCount = p;
                        steps.add(s);
                    }
                }
            }

            // Setup video encoder
            TimelapseJobManager.updateProgress(job.jobId, 30, "Initializing video encoder...");
            int totalFrames = Math.max(1, durationSeconds * FRAME_RATE);
            String videoFilename = job.jobId + ".mp4";
            Path videoPath = Paths.get(TIMELAPSE_DIR, videoFilename);

            SeekableByteChannel channel = NIOUtils.writableFileChannel(videoPath.toString());
            AWTSequenceEncoder encoder = new AWTSequenceEncoder(channel, Rational.R(FRAME_RATE, 1));

            // Generate frames by mapping timeline steps into frames.
            TimelapseJobManager.updateProgress(job.jobId, 40, "Rendering frames...");

            int totalSteps = steps.size();
            if (totalSteps == 0) totalSteps = 1; // avoid division by zero

            for (int frameNum = 0; frameNum < totalFrames; frameNum++) {
                // Determine how many steps should be visible at this frame.
                int stepsToShow = (int) Math.round(((frameNum + 1) / (double) totalFrames) * totalSteps);
                stepsToShow = Math.min(stepsToShow, totalSteps);

                BufferedImage frame = renderFrame(boardData, steps, stepsToShow);
                encoder.encodeImage(frame);

                // Update progress (40% to 90%)
                int progress = 40 + (int)((frameNum / (double)totalFrames) * 50);
                if (frameNum % 5 == 0) {
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
     * Includes both shapes and strokes for complete timelapse
     */
    private static BufferedImage renderFrame(BoardData boardData, java.util.List<Step> steps, int stepsToShow) {
        BufferedImage image = new BufferedImage(VIDEO_WIDTH, VIDEO_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Enable anti-aliasing for smoother shapes
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // White background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);

        // Determine how many shapes to show and how many points per stroke to reveal
        int shapeCountVisible = 0;
        int strokeCount = boardData.strokes != null ? boardData.strokes.size() : 0;
        int[] strokePointCounts = new int[Math.max(0, strokeCount)];

        for (int i = 0; i < stepsToShow && i < steps.size(); i++) {
            Step s = steps.get(i);
            if (s == null || s.kind == null) continue;
            if ("shape".equals(s.kind)) {
                shapeCountVisible++;
            } else if ("stroke".equals(s.kind)) {
                if (s.strokeIndex >= 0 && s.strokeIndex < strokePointCounts.length) {
                    strokePointCounts[s.strokeIndex] = Math.max(strokePointCounts[s.strokeIndex], s.pointCount);
                }
            }
        }

        // Draw strokes (progressively up to their revealed point counts)
        if (boardData.strokes != null) {
            for (int si = 0; si < boardData.strokes.size(); si++) {
                BoardStorageService.StrokeData sd = boardData.strokes.get(si);
                int to = Math.min(sd.points.size(), strokePointCounts[si]);
                if (to <= 1) continue; // need at least 2 points to draw a segment
                for (int p = 1; p < to; p++) {
                    BoardStorageService.DrawPoint a = sd.points.get(p - 1);
                    BoardStorageService.DrawPoint b = sd.points.get(p);
                    g2d.setColor(parseColor(a.color));
                    g2d.setStroke(new BasicStroke((float)a.size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.drawLine((int)a.x, (int)a.y, (int)b.x, (int)b.y);
                }
            }
        }

        // Draw shapes on top
        if (boardData.shapes != null) {
            for (int i = 0; i < shapeCountVisible && i < boardData.shapes.size(); i++) {
                renderShape(g2d, boardData.shapes.get(i));
            }
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
