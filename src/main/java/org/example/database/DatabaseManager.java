package org.example.database;

import java.sql.*;

/**
 * Database Manager - Handles SQLite connection and initialization
 * Follows Single Responsibility Principle
 */
public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:whiteboard_users.db";
    private Connection connection;

    public DatabaseManager() {
        initializeConnection();
    }

    /**
     * Initialize database connection
     */
    private void initializeConnection() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            System.out.println("✅ Database connection established");
        } catch (SQLException e) {
            System.err.println("❌ Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get database connection
     * @return Active database connection
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initializeConnection();
            }
        } catch (SQLException e) {
            System.err.println("❌ Connection check failed: " + e.getMessage());
        }
        return connection;
    }

    /**
     * Close database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✅ Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("⚠️ Error closing database: " + e.getMessage());
        }
    }

    /**
     * Check if connection is active
     * @return true if connected
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
