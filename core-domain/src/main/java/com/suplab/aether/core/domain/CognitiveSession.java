package com.suplab.aether.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A multi-turn reasoning context for a user within a tenant.
 *
 * <p>Sessions capture the emotional arc and engagement level across an interaction,
 * allowing subsequent agent decisions to be tuned to the user's current cognitive state.</p>
 *
 * <p>Turn summaries are an immutable defensive copy — the session is itself immutable.</p>
 */
public record CognitiveSession(
        UUID sessionId,
        String userId,
        String tenantId,
        List<String> turnSummaries,
        String emotionalState,
        double engagementScore,
        Instant startedAt,
        Instant lastActiveAt
) {
    public CognitiveSession {
        if (sessionId == null) sessionId = UUID.randomUUID();
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        turnSummaries = turnSummaries != null ? List.copyOf(turnSummaries) : List.of();
        if (emotionalState == null || emotionalState.isBlank()) emotionalState = "NEUTRAL";
        if (engagementScore < 0 || engagementScore > 1) engagementScore = 0.5;
        if (startedAt == null) startedAt = Instant.now();
        if (lastActiveAt == null) lastActiveAt = startedAt;
    }
}
