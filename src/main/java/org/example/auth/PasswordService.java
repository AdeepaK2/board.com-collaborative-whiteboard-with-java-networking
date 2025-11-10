package org.example.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password Service - Handles password hashing and verification
 * Uses BCrypt for secure password hashing
 */
public class PasswordService {
    
    /**
     * Hash a plain text password
     * @param plainPassword Plain text password
     * @return BCrypt hashed password
     */
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    /**
     * Verify password against stored hash
     * @param plainPassword Plain text password
     * @param hashedPassword Stored BCrypt hash
     * @return true if password matches
     */
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ Invalid hash format: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validate password strength
     * @param password Password to validate
     * @return true if password meets requirements
     */
    public boolean isPasswordValid(String password) {
        if (password == null || password.length() < 4) {
            return false;
        }
        // Add more validation rules as needed
        // e.g., require uppercase, numbers, special chars
        return true;
    }
}
