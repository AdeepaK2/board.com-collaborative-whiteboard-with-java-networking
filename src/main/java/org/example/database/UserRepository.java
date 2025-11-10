package org.example.database;

import java.sql.*;

/**
 * User Repository - Handles database operations for users
 * Data Access Layer following Repository Pattern
 */
public class UserRepository {
    private final DatabaseManager dbManager;

    public UserRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        initializeTable();
    }

    /**
     * Create users table if not exists
     */
    private void initializeTable() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_login TIMESTAMP
            )
        """;
        
        try (Statement stmt = dbManager.getConnection().createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("✅ Users table initialized");
        } catch (SQLException e) {
            System.err.println("❌ Failed to create users table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Insert new user into database
     * @param username Username
     * @param passwordHash Hashed password
     * @return true if successful
     */
    public boolean insertUser(String username, String passwordHash) {
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            pstmt.setString(2, passwordHash);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                System.out.println("⚠️ Username already exists: " + username);
            } else {
                System.err.println("❌ Failed to insert user: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Get password hash for username
     * @param username Username
     * @return Password hash or null if not found
     */
    public String getPasswordHash(String username) {
        String sql = "SELECT password_hash FROM users WHERE username = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("password_hash");
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to get password hash: " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if user exists
     * @param username Username
     * @return true if exists
     */
    public boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error checking user existence: " + e.getMessage());
        }
        return false;
    }

    /**
     * Update last login timestamp
     * @param username Username
     */
    public void updateLastLogin(String username) {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE username = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username.trim());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("⚠️ Failed to update last login: " + e.getMessage());
        }
    }

    /**
     * Update user password
     * @param username Username
     * @param newPasswordHash New password hash
     * @return true if successful
     */
    public boolean updatePassword(String username, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE username = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setString(2, username.trim());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("❌ Failed to update password: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get total user count
     * @return Number of users
     */
    public int getUserCount() {
        String sql = "SELECT COUNT(*) FROM users";
        
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error getting user count: " + e.getMessage());
        }
        return 0;
    }
}
