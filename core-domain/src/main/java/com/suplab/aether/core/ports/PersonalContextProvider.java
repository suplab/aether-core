package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.PersonalContext;

import java.util.Optional;

/**
 * Port interface for assembling a user's personal context snapshot.
 *
 * <p>Implementations retrieve relevant memories, compute emotional state and engagement
 * score, and assemble the {@link PersonalContext} record served to Aether Grid.</p>
 *
 * <p>Returns {@link Optional#empty()} when the user has no data in Core — callers should
 * handle this gracefully and proceed with a default context.</p>
 */
public interface PersonalContextProvider {

    /**
     * Assembles a personal context snapshot for the given user in the given tenant.
     *
     * @param tenantId the tenant scope (required for multi-tenant isolation)
     * @param userId   the user whose context to assemble
     * @return personal context snapshot, or empty if the user has no stored data
     */
    Optional<PersonalContext> buildContext(String tenantId, String userId);
}
