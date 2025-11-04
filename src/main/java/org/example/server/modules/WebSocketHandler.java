package org.example.server.modules;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Handles WebSocket protocol operations
 * 
 * LESSON 9: HTTP Protocol - WebSocket uses HTTP upgrade mechanism
 */
public class WebSocketHandler {
    private static final String WEBSOCKET_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    /**
     * Generate WebSocket accept key for handshake
     * 
     * LESSON 8: Uses SHA-1 hashing (though not for security here, just protocol requirement)
     */
    public static String generateWebSocketAccept(String key) throws NoSuchAlgorithmException {
        String combined = key + WEBSOCKET_MAGIC_STRING;
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(combined.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
    
    /**
     * Decode WebSocket frame to extract message
     * 
     * Handles WebSocket frame protocol:
     * - FIN bit (final fragment)
     * - Opcode (text/binary/close/ping/pong)
     * - Mask bit and masking key
     * - Payload length (7-bit, 16-bit, or 64-bit)
     */
    public static String decodeWebSocketFrame(byte[] buffer, int length) {
        try {
            if (length < 2) return null;
            
            // Parse frame header
            boolean fin = (buffer[0] & 0x80) != 0;
            int opcode = buffer[0] & 0x0F;
            boolean masked = (buffer[1] & 0x80) != 0;
            int payloadLength = buffer[1] & 0x7F;
            
            // Only handle text frames
            if (opcode != 0x1 || !fin) {
                return null;
            }
            
            int offset = 2;
            
            // Handle extended payload length
            if (payloadLength == 126) {
                if (length < 4) return null;
                payloadLength = ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                offset = 4;
            } else if (payloadLength == 127) {
                offset = 10; // 64-bit length
            }
            
            // Extract masking key
            byte[] maskingKey = new byte[4];
            if (masked) {
                if (length < offset + 4) return null;
                System.arraycopy(buffer, offset, maskingKey, 0, 4);
                offset += 4;
            }
            
            // Extract and unmask payload
            if (length < offset + payloadLength) {
                payloadLength = length - offset;
            }
            
            byte[] payload = new byte[payloadLength];
            System.arraycopy(buffer, offset, payload, 0, payloadLength);
            
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskingKey[i % 4];
                }
            }
            
            return new String(payload, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Error decoding frame: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Encode message into WebSocket frame format
     */
    public static byte[] encodeWebSocketFrame(String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        byte[] frame;
        
        if (payload.length < 126) {
            frame = new byte[2 + payload.length];
            frame[0] = (byte) 0x81; // FIN + text frame
            frame[1] = (byte) payload.length;
            System.arraycopy(payload, 0, frame, 2, payload.length);
        } else if (payload.length < 65536) {
            frame = new byte[4 + payload.length];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) 126;
            frame[2] = (byte) ((payload.length >> 8) & 0xFF);
            frame[3] = (byte) (payload.length & 0xFF);
            System.arraycopy(payload, 0, frame, 4, payload.length);
        } else {
            frame = new byte[10 + payload.length];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) 127;
            // For simplicity, only using last 4 bytes of 64-bit length
            for (int i = 2; i < 10; i++) {
                frame[i] = 0;
            }
            System.arraycopy(payload, 0, frame, 10, payload.length);
        }
        
        return frame;
    }
}
