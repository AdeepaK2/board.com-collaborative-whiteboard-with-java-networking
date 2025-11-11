package org.example.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages asynchronous timelapse video generation jobs
 * 
 * NETWORK PROGRAMMING PRINCIPLE: Asynchronous Job Processing
 * - Tracks long-running video generation tasks
 * - Provides status updates for HTTP polling
 * - Demonstrates stateful server pattern
 */
public class TimelapseJobManager {
    
    public enum JobStatus {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    public static class TimelapseJob {
        public String jobId;
        public String boardId;
        public JobStatus status;
        public int progress; // 0-100
        public String message;
        public String videoPath;
        public long createdAt;
        public long completedAt;
        
        public TimelapseJob(String boardId) {
            this.jobId = "job-" + UUID.randomUUID().toString().substring(0, 8);
            this.boardId = boardId;
            this.status = JobStatus.QUEUED;
            this.progress = 0;
            this.message = "Job queued";
            this.createdAt = System.currentTimeMillis();
        }
    }
    
    // Thread-safe job tracking
    private static final Map<String, TimelapseJob> jobs = new ConcurrentHashMap<>();
    
    /**
     * Create a new timelapse job
     */
    public static TimelapseJob createJob(String boardId) {
        TimelapseJob job = new TimelapseJob(boardId);
        jobs.put(job.jobId, job);
        System.out.println("🎬 Created timelapse job: " + job.jobId + " for board: " + boardId);
        return job;
    }
    
    /**
     * Get job status
     */
    public static TimelapseJob getJob(String jobId) {
        return jobs.get(jobId);
    }
    
    /**
     * Update job progress
     */
    public static void updateProgress(String jobId, int progress, String message) {
        TimelapseJob job = jobs.get(jobId);
        if (job != null) {
            job.progress = progress;
            job.message = message;
            System.out.println("📊 Job " + jobId + ": " + progress + "% - " + message);
        }
    }
    
    /**
     * Mark job as processing
     */
    public static void markProcessing(String jobId) {
        TimelapseJob job = jobs.get(jobId);
        if (job != null) {
            job.status = JobStatus.PROCESSING;
            job.message = "Generating video...";
            System.out.println("▶️ Job " + jobId + " started processing");
        }
    }
    
    /**
     * Mark job as completed
     */
    public static void markCompleted(String jobId, String videoPath) {
        TimelapseJob job = jobs.get(jobId);
        if (job != null) {
            job.status = JobStatus.COMPLETED;
            job.progress = 100;
            job.message = "Video ready";
            job.videoPath = videoPath;
            job.completedAt = System.currentTimeMillis();
            System.out.println("✅ Job " + jobId + " completed: " + videoPath);
        }
    }
    
    /**
     * Mark job as failed
     */
    public static void markFailed(String jobId, String errorMessage) {
        TimelapseJob job = jobs.get(jobId);
        if (job != null) {
            job.status = JobStatus.FAILED;
            job.message = errorMessage;
            job.completedAt = System.currentTimeMillis();
            System.err.println("❌ Job " + jobId + " failed: " + errorMessage);
        }
    }
    
    /**
     * Clean up old completed jobs (optional)
     */
    public static void cleanupOldJobs(long maxAgeMs) {
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(entry -> {
            TimelapseJob job = entry.getValue();
            return (job.status == JobStatus.COMPLETED || job.status == JobStatus.FAILED) 
                   && (now - job.completedAt) > maxAgeMs;
        });
    }
}
