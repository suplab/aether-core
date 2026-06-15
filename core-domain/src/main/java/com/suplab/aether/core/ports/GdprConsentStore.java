package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.GdprConsent;

import java.util.Optional;

/**
 * Port interface for GDPR consent persistence.
 *
 * <p>Consent records gate memory storage for each user. Before any memory is stored,
 * the adapter checks consent and skips persistence when the user has opted out.</p>
 */
public interface GdprConsentStore {

    /**
     * Persists or updates a consent record (upsert by userId + tenantId).
     *
     * @param consent the consent record to store
     */
    void save(GdprConsent consent);

    /**
     * Returns the consent record for a user, if one exists.
     *
     * @param userId   the user's identifier
     * @param tenantId the tenant scope
     * @return the consent record, or empty if none has been explicitly set
     */
    Optional<GdprConsent> findByUser(String userId, String tenantId);

    /**
     * Returns {@code true} if memory storage is allowed for this user.
     * Defaults to {@code true} when no explicit record exists (opt-in by default).
     *
     * @param userId   the user's identifier
     * @param tenantId the tenant scope
     * @return whether memory storage is permitted
     */
    default boolean isMemoryStorageAllowed(String userId, String tenantId) {
        return findByUser(userId, tenantId)
                .map(GdprConsent::memoryStorageAllowed)
                .orElse(true);
    }
}
