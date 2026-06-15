package com.suplab.aether.core.domain;

import java.time.Instant;

/**
 * Records a user's GDPR consent preferences for memory storage in Aether Core.
 *
 * <p>When {@code memoryStorageAllowed} is {@code false}, no new memories are persisted
 * for this user and existing memories are queued for erasure. This is the enforcement
 * point for GDPR Article 17 (Right to Erasure) within Aether Core.</p>
 */
public record GdprConsent(
        String userId,
        String tenantId,
        boolean memoryStorageAllowed,
        int dataRetentionDays,
        Instant consentRecordedAt,
        Instant updatedAt
) {
    public GdprConsent {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId required");
        if (dataRetentionDays < 0) throw new IllegalArgumentException("dataRetentionDays must be >= 0");
        if (consentRecordedAt == null) consentRecordedAt = Instant.now();
        if (updatedAt == null) updatedAt = consentRecordedAt;
    }

    /**
     * Creates a default consent record allowing memory storage with 365-day retention.
     */
    public static GdprConsent defaultConsent(String userId, String tenantId) {
        return new GdprConsent(userId, tenantId, true, 365, Instant.now(), Instant.now());
    }

    /**
     * Creates a consent record opting out of memory storage.
     */
    public static GdprConsent optOut(String userId, String tenantId) {
        return new GdprConsent(userId, tenantId, false, 0, Instant.now(), Instant.now());
    }
}
