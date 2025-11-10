package org.example.auth;

/**
 * Authentication Result - Immutable result object for auth operations
 * Follows Result Pattern for clear success/failure handling
 */
public class AuthResult {
    private final boolean success;
    private final String message;
    private final String username;

    private AuthResult(boolean success, String message, String username) {
        this.success = success;
        this.message = message;
        this.username = username;
    }

    /**
     * Create successful result
     * @param message Success message
     * @param username Username
     * @return AuthResult
     */
    public static AuthResult success(String message, String username) {
        return new AuthResult(true, message, username);
    }

    /**
     * Create failure result
     * @param message Error message
     * @return AuthResult
     */
    public static AuthResult failure(String message) {
        return new AuthResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "AuthResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
}
