package com.suplab.aether.core.memory.context;

import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalContext;
import com.suplab.aether.core.ports.PersonalContextProvider;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles a {@link PersonalContext} snapshot from stored personal memories.
 *
 * <p>Returns {@link Optional#empty()} when the user has no memories at all — callers
 * (e.g. {@code PersonalContextController} and Aether Grid) treat this as a no-context
 * signal and proceed with defaults.</p>
 */
public class DefaultPersonalContextProvider implements PersonalContextProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersonalContextProvider.class);

    private final PersonalMemoryStore memoryStore;
    private final int defaultMemoryLimit;

    public DefaultPersonalContextProvider(PersonalMemoryStore memoryStore, int defaultMemoryLimit) {
        this.memoryStore = memoryStore;
        this.defaultMemoryLimit = defaultMemoryLimit;
    }

    @Override
    public Optional<PersonalContext> buildContext(String tenantId, String userId) {
        var episodic = memoryStore.findByType(userId, MemoryType.EPISODIC, defaultMemoryLimit);
        var semantic  = memoryStore.findByType(userId, MemoryType.SEMANTIC,  defaultMemoryLimit);
        var emotional = memoryStore.findByType(userId, MemoryType.EMOTIONAL, 2);

        if (episodic.isEmpty() && semantic.isEmpty() && emotional.isEmpty()) {
            log.debug("No memories found for userId={} tenantId={} — returning empty context", userId, tenantId);
            return Optional.empty();
        }

        List<String> summaries = new ArrayList<>();
        episodic.forEach(m -> summaries.add(m.content()));
        semantic.forEach(m  -> summaries.add(m.content()));

        var emotionalState = emotional.isEmpty()
                ? "NEUTRAL"
                : emotional.getFirst().content().toUpperCase();

        var engagementScore = episodic.isEmpty()
                ? 0.5
                : episodic.stream().mapToDouble(m -> m.strength()).average().orElse(0.5);

        var context = new PersonalContext(
                userId,
                tenantId,
                summaries,
                Map.of(),
                emotionalState,
                Math.min(1.0, engagementScore),
                Instant.now()
        );

        log.debug("Built personal context userId={} tenantId={} summaries={} emotionalState={}",
                userId, tenantId, summaries.size(), emotionalState);
        return Optional.of(context);
    }
}
