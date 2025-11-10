package org.example.auth;

import org.example.database.UserRepository;

/**
 * Authentication Service - Business logic for user authentication
 * Coordinates between PasswordService and UserRepository
 */
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordService = new PasswordService();
    }

    /**
     * Register a new user
     * @param username Username
     * @param password Plain text password
     * @return AuthResult with success status and message
     */
    public AuthResult register(String username, String password) {
        // Validate input
        if (username == null || username.trim().isEmpty()) {
            return AuthResult.failure("Username cannot be empty");
        }

        if (!passwordService.isPasswordValid(password)) {
            return AuthResult.failure("Password must be at least 4 characters");
        }

        // Check if user already exists
        if (userRepository.userExists(username)) {
            return AuthResult.failure("Username already exists");
        }

        // Hash password and store user
        String passwordHash = passwordService.hashPassword(password);
        boolean success = userRepository.insertUser(username, passwordHash);

        if (success) {
            System.out.println("✅ User registered: " + username);
            return AuthResult.success("Registration successful", username);
        } else {
            return AuthResult.failure("Registration failed");
        }
    }

    /**
     * Login user with credentials
     * @param username Username
     * @param password Plain text password
     * @return AuthResult with success status and message
     */
    public AuthResult login(String username, String password) {
        // Validate input
        if (username == null || username.trim().isEmpty() || 
            password == null || password.trim().isEmpty()) {
            return AuthResult.failure("Username and password are required");
        }

        // Get stored password hash
        String storedHash = userRepository.getPasswordHash(username);
        
        if (storedHash == null) {
            System.out.println("❌ User not found: " + username);
            return AuthResult.failure("Invalid username or password");
        }

        // Verify password
        if (passwordService.verifyPassword(password, storedHash)) {
            userRepository.updateLastLogin(username);
            System.out.println("✅ Login successful: " + username);
            return AuthResult.success("Login successful", username);
        } else {
            System.out.println("❌ Invalid password for: " + username);
            return AuthResult.failure("Invalid username or password");
        }
    }

    /**
     * Change user password
     * @param username Username
     * @param oldPassword Current password
     * @param newPassword New password
     * @return AuthResult with success status
     */
    public AuthResult changePassword(String username, String oldPassword, String newPassword) {
        // First verify old password
        AuthResult loginResult = login(username, oldPassword);
        if (!loginResult.isSuccess()) {
            return AuthResult.failure("Current password is incorrect");
        }

        // Validate new password
        if (!passwordService.isPasswordValid(newPassword)) {
            return AuthResult.failure("New password must be at least 4 characters");
        }

        // Hash and update password
        String newHash = passwordService.hashPassword(newPassword);
        boolean success = userRepository.updatePassword(username, newHash);

        if (success) {
            System.out.println("✅ Password changed for: " + username);
            return AuthResult.success("Password changed successfully", username);
        } else {
            return AuthResult.failure("Failed to change password");
        }
    }

    /**
     * Check if username exists
     * @param username Username to check
     * @return true if exists
     */
    public boolean userExists(String username) {
        return userRepository.userExists(username);
    }

    /**
     * Get total registered users count
     * @return Number of users
     */
    public int getUserCount() {
        return userRepository.getUserCount();
    }
}
