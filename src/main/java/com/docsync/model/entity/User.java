package com.docsync.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * JPA entity representing a registered user.
 * Implements {@link UserDetails} directly to satisfy Spring Security's authentication contract
 * without requiring a separate adapter class (RC-002).
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Default no-arg constructor required by JPA.
     */
    public User() {
    }

    /**
     * Sets {@code createdAt} and {@code updatedAt} before the entity is first persisted.
     */
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Updates {@code updatedAt} before every subsequent database write.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the user's email address as the Spring Security username.
     *
     * @return email address
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Returns the BCrypt-hashed password stored for this user.
     *
     * @return password hash
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * Returns the authorities granted to this user.
     * All registered users receive {@code ROLE_USER} (ADR-007).
     *
     * @return collection containing {@code ROLE_USER}
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /**
     * Returns {@code true} — accounts do not expire in this iteration.
     *
     * @return true always
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Returns {@code true} — account locking is handled by {@code LoginAttemptService},
     * not by the Spring Security account-locked flag.
     *
     * @return true always
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Returns {@code true} — credentials do not expire in this iteration.
     *
     * @return true always
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Returns whether this user account is enabled.
     *
     * @return {@code true} if the account is active
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the user's unique identifier.
     *
     * @return UUID primary key
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the user's unique identifier.
     *
     * @param id UUID primary key
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Returns the user's email address.
     *
     * @return email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the user's email address.
     *
     * @param email email address
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the BCrypt password hash.
     *
     * @return password hash
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Sets the BCrypt password hash.
     *
     * @param passwordHash BCrypt hash of the user's password
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Sets the enabled flag.
     *
     * @param enabled true if the account should be active
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the timestamp when this user record was created.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the timestamp of the most recent update to this user record.
     *
     * @return last-updated timestamp
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
