package com.suplab.aether.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A point-in-time snapshot of a user's personal cognitive context.
 *
 * <p>Assembled on-demand by {@link com.suplab.aether.core.ports.PersonalContextProvider}
 * and served to Aether Grid via {@code GET /api/v1/personal-context/{tenantId}/{userId}}.
 * Grid's {@code AetherCoreBridgeAgent} uses this to enrich agent decisions with personal
 * memory summaries, preferences, and emotional state.</p>
 *
 * <p>Both collections are defensively copied to preserve immutability.</p>
 */
public record PersonalContext(
        String userId,
        String tenantId,
        List<String> recentMemorySummaries,
        Map<String, Object> preferences,
        String emotionalState,
        double engagementScore,
        Instant fetchedAt
) {
    public PersonalContext {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        recentMemorySummaries = recentMemorySummaries != null
                ? List.copyOf(recentMemorySummaries) : List.of();
        preferences = preferences != null ? Map.copyOf(preferences) : Map.of();
        if (emotionalState == null || emotionalState.isBlank()) emotionalState = "NEUTRAL";
        if (engagementScore < 0 || engagementScore > 1) engagementScore = 0.5;
        if (fetchedAt == null) fetchedAt = Instant.now();
    }
}
