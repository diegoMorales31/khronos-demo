package com.moga.khronosdemo.entities;

import com.moga.khronosdemo.entities.enums.NotificationType;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/** A notice in a user's in-app list (V20). */
@Entity
@Table(name = "notifications")
public class InboxNotification {

    @Id
    // Identity (V21): every insert is one statement, with no block of ids to fetch now and then
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    // JSON, whose shape depends on the type
    @Column(nullable = false, columnDefinition = "text")
    private String data;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    public InboxNotification() {
    }

    public InboxNotification(User user, NotificationType type, String data, Instant createdAt) {
        this.user = user;
        this.type = type;
        this.data = data;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public NotificationType getType() {
        return type;
    }

    public String getData() {
        return data;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }
}
