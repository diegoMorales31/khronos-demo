package com.moga.khronosdemo.security;

public interface RefreshTokenService {

    /** Starts a session for the user and returns its refresh token, which is never stored as is. */
    String start(Long userId);

    /** Swaps a refresh token for a new one; throws CredentialsExpiredException when it can't be used. */
    Rotation rotate(String refreshToken);

    /** Ends the session of this token, if it still has one. */
    void end(String refreshToken);

    /** Ends every session of the user: a password change or reset, a deactivation. */
    void endAll(Long userId);

    record Rotation(Long userId, String refreshToken) {
    }
}
