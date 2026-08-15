package com.docsync.repository;

import com.docsync.model.entity.RefreshToken;
import com.docsync.model.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link RefreshToken} entities.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Finds a refresh token record by the raw token string.
     *
     * @param token the raw JWT refresh token string
     * @return an Optional containing the token record if found, or empty if not found
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Deletes all refresh token records belonging to the specified user.
     * Used during logout to revoke all active refresh tokens for a user.
     *
     * @param user the user whose refresh tokens should be deleted
     */
    void deleteByUser(User user);

    /**
     * Deletes all refresh tokens that expired before the given instant.
     * Used by the scheduled cleanup task to prevent unbounded table growth.
     *
     * @param now the cutoff instant; tokens with {@code expiresAt} before this will be deleted
     * @return the number of token records deleted
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") Instant now);
}
