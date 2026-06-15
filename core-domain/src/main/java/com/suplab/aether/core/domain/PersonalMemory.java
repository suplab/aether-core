package com.suplab.aether.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A single personal memory associated with a user.
 *
 * <p>Memory strength ranges from 0.0 (faded) to 1.0 (vivid). Strength increases on
 * reinforcement (via {@link #reinforce()}) and decays over time via a scheduled job in
 * {@code core-api}. Memories below a configured threshold are purged.</p>
 *
 * <p>All fields are immutable. Use {@link #create(String, MemoryType, String)} for new
 * memories and {@link #reinforce()} to produce a reinforced copy on access.</p>
 */
public record PersonalMemory(
        UUID id,
        String userId,
        MemoryType type,
        String content,
        double strength,
        int accessCount,
        Instant createdAt,
        Instant lastAccessedAt
) {
    public PersonalMemory {
        if (id == null) id = UUID.randomUUID();
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (type == null) throw new IllegalArgumentException("type required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("content required");
        if (strength < 0 || strength > 1) throw new IllegalArgumentException("strength must be 0-1");
        if (createdAt == null) createdAt = Instant.now();
        if (lastAccessedAt == null) lastAccessedAt = createdAt;
    }

    /**
     * Factory method for new memories. Assigns a random ID and sets initial strength to 1.0.
     */
    public static PersonalMemory create(String userId, MemoryType type, String content) {
        return new PersonalMemory(UUID.randomUUID(), userId, type, content, 1.0, 0,
                Instant.now(), Instant.now());
    }

    /**
     * Returns a new instance with strength increased by 0.1 (capped at 1.0) and
     * accessCount incremented. Call on every successful retrieval to reinforce the memory.
     */
    public PersonalMemory reinforce() {
        double newStrength = Math.min(1.0, strength + 0.1);
        return new PersonalMemory(id, userId, type, content, newStrength, accessCount + 1,
                createdAt, Instant.now());
    }
}
