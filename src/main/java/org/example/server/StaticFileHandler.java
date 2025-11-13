package org.example.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handler for serving static files (images)
 */
public class StaticFileHandler implements HttpHandler {
    private final String baseDirectory;

    public StaticFileHandler(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Enable CORS
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        
        // Remove /images/ prefix to get the filename
        String filename = requestPath.substring("/images/".length());
        
        // Security: Prevent directory traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        Path filePath = Paths.get(baseDirectory, filename);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        // Determine content type based on file extension
        String contentType = getContentType(filename);
        exchange.getResponseHeaders().set("Content-Type", contentType);

        // Read file and send response
        byte[] fileData = Files.readAllBytes(filePath);
        exchange.sendResponseHeaders(200, fileData.length);
        
        OutputStream os = exchange.getResponseBody();
        os.write(fileData);
        os.close();
    }

    private String getContentType(String filename) {
        String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerName.endsWith(".png")) {
            return "image/png";
        } else if (lowerName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerName.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerName.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}

