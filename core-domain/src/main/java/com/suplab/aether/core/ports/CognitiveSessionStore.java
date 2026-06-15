package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.CognitiveSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for cognitive session persistence.
 *
 * <p>Cognitive sessions capture the emotional arc and engagement level across a
 * multi-turn user interaction. Sessions are created on first turn, updated on
 * subsequent turns, and closed when the interaction ends or times out.</p>
 */
public interface CognitiveSessionStore {

    /**
     * Persists a new session or updates an existing one (upsert semantics).
     *
     * @param session the session to persist
     */
    void save(CognitiveSession session);

    /**
     * Returns the active session for a user in a tenant, if one exists.
     *
     * @param userId   the user's identifier
     * @param tenantId the tenant scope
     * @return the active session, or empty if none exists
     */
    Optional<CognitiveSession> findActiveSession(String userId, String tenantId);

    /**
     * Returns a session by its unique ID.
     *
     * @param sessionId the session UUID
     * @return the session, or empty if not found
     */
    Optional<CognitiveSession> findById(UUID sessionId);

    /**
     * Returns the most recent sessions for a user, ordered by last-active descending.
     *
     * @param userId the user's identifier
     * @param limit  maximum number of sessions to return
     * @return ordered list of recent sessions
     */
    List<CognitiveSession> findRecentByUser(String userId, int limit);

    /**
     * Closes a session by its ID, marking it inactive.
     *
     * @param sessionId the session to close
     */
    void close(UUID sessionId);

    /**
     * Hard-deletes all sessions for a user. Used for GDPR right-to-erasure.
     *
     * @param userId   the user whose sessions to erase
     * @param tenantId the tenant scope (enforces isolation)
     * @return number of sessions deleted
     */
    int eraseByUser(String userId, String tenantId);
}
