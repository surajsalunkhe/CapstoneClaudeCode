package com.docsync.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a server-side refresh token record.
 * Stored in the {@code refresh_tokens} table. Enables server-side token revocation on logout.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Default no-arg constructor required by JPA.
     */
    public RefreshToken() {
    }

    /**
     * Sets {@code createdAt} before the entity is first persisted.
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    /**
     * Returns the UUID primary key.
     *
     * @return unique identifier
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the UUID primary key.
     *
     * @param id unique identifier
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the raw JWT refresh token string.
     *
     * @return refresh token string
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets the raw JWT refresh token string.
     *
     * @param token refresh token string
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Returns the user associated with this refresh token.
     *
     * @return owning user
     */
    public User getUser() {
        return user;
    }

    /**
     * Sets the user associated with this refresh token.
     *
     * @param user owning user
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Returns when this refresh token expires.
     *
     * @return expiry instant
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets when this refresh token expires.
     *
     * @param expiresAt expiry instant
     */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the timestamp when this token record was created.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
