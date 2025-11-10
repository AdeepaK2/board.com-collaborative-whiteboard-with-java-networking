package org.example.service;

import org.example.database.DatabaseManager;
import org.example.database.UserRepository;
import org.example.auth.AuthenticationService;
import org.example.auth.AuthResult;

/**
 * User Database Service - Facade for authentication operations
 * Provides simplified interface for user management
 */
public class UserDatabaseService {
    private final DatabaseManager dbManager;
    private final UserRepository userRepository;
    private final AuthenticationService authService;

    public UserDatabaseService() {
        this.dbManager = new DatabaseManager();
        this.userRepository = new UserRepository(dbManager);
        this.authService = new AuthenticationService(userRepository);
        System.out.println("✅ User database service initialized");
    }

    /**
     * Register a new user
     * @param username Username
     * @param password Plain text password (will be hashed)
     * @return true if registration successful
     */
    public boolean registerUser(String username, String password) {
        AuthResult result = authService.register(username, password);
        return result.isSuccess();
    }

    /**
     * Login user with username and password
     * @param username Username
     * @param password Plain text password
     * @return true if login successful
     */
    public boolean loginUser(String username, String password) {
        AuthResult result = authService.login(username, password);
        return result.isSuccess();
    }

    /**
     * Check if username exists
     * @param username Username to check
     * @return true if exists
     */
    public boolean userExists(String username) {
        return authService.userExists(username);
    }

    /**
     * Change user password
     * @param username Username
     * @param oldPassword Current password
     * @param newPassword New password
     * @return true if password changed successfully
     */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        AuthResult result = authService.changePassword(username, oldPassword, newPassword);
        return result.isSuccess();
    }

    /**
     * Get total user count
     * @return Number of registered users
     */
    public int getUserCount() {
        return authService.getUserCount();
    }

    /**
     * Close database connection
     */
    public void close() {
        dbManager.close();
    }
}
