package com.moga.khronosdemo.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** A signed-in device: the hash of its current refresh token and of the one before (V19). */
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(length = 64)
    private String previousHash;

    private Instant rotatedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    public RefreshSession() {
    }

    public RefreshSession(User user, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // The token was used: the new one becomes current and the used one is remembered to spot its reuse
    public void rotate(String newHash, Instant now, Instant newExpiry) {
        this.previousHash = this.tokenHash;
        this.tokenHash = newHash;
        this.rotatedAt = now;
        this.expiresAt = newExpiry;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
