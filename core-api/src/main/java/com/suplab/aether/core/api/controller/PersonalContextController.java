package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.PersonalContext;
import com.suplab.aether.core.ports.PersonalContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Serves the personal context snapshot consumed by Aether Grid.
 *
 * <p>The key endpoint is {@code GET /api/v1/personal-context/{tenantId}/{userId}}.
 * Aether Grid's {@code AetherCoreHttpAdapter} calls this before agent decisions so that
 * {@code AetherCoreBridgeAgent} can enrich {@code AgentInput.context} with personal
 * memories, emotional state, and engagement score.</p>
 *
 * <p>When the user has no stored memories the endpoint returns a neutral default context
 * (HTTP 200) rather than 404 — Grid callers always receive a usable response.</p>
 */
@RestController
@RequestMapping("/api/v1/personal-context")
public class PersonalContextController {

    private static final Logger log = LoggerFactory.getLogger(PersonalContextController.class);

    private final PersonalContextProvider contextProvider;

    public PersonalContextController(PersonalContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    /**
     * Returns the personal context snapshot for a user within a tenant.
     *
     * @param tenantId    the tenant scope (required for multi-tenant isolation)
     * @param userId      the user whose context to assemble
     * @param memoryLimit maximum number of memories per type (hint passed to provider, default 5)
     * @return personal context as a JSON map, always HTTP 200
     */
    @GetMapping("/{tenantId}/{userId}")
    public ResponseEntity<Map<String, Object>> getContext(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @RequestParam(defaultValue = "5") int memoryLimit) {

        log.debug("Fetching personal context tenantId={} userId={} limit={}", tenantId, userId, memoryLimit);

        var context = contextProvider.buildContext(tenantId, userId)
                .orElseGet(() -> emptyContext(tenantId, userId));

        var body = Map.<String, Object>of(
                "userId", context.userId(),
                "tenantId", context.tenantId(),
                "recentMemorySummaries", context.recentMemorySummaries(),
                "preferences", context.preferences(),
                "emotionalState", context.emotionalState(),
                "engagementScore", context.engagementScore(),
                "fetchedAt", context.fetchedAt().toString()
        );

        log.debug("Personal context assembled userId={} summaries={} emotionalState={}",
                userId, context.recentMemorySummaries().size(), context.emotionalState());
        return ResponseEntity.ok(body);
    }

    private static PersonalContext emptyContext(String tenantId, String userId) {
        return new PersonalContext(userId, tenantId, List.of(), Map.of(), "NEUTRAL", 0.5, Instant.now());
    }
}
