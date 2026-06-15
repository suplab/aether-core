package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for cognitive session management.
 *
 * <p>Sessions capture the emotional arc and engagement level across a multi-turn user
 * interaction. A session is created on the first turn, updated with each subsequent
 * turn summary, and closed explicitly or via the weekly expiry scheduler.</p>
 *
 * <h2>Typical Flow</h2>
 * <ol>
 *   <li>{@code POST /api/v1/sessions} — create or resume a session</li>
 *   <li>{@code PUT  /api/v1/sessions/{sessionId}/turns} — add turn summaries</li>
 *   <li>{@code POST /api/v1/sessions/{sessionId}/close} — close the session</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class CognitiveSessionController {

    private static final Logger log = LoggerFactory.getLogger(CognitiveSessionController.class);

    private final CognitiveSessionStore sessionStore;

    public CognitiveSessionController(CognitiveSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * Creates a new cognitive session. If an active session already exists for the
     * user/tenant, the existing session is returned rather than creating a duplicate.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrResume(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String tenantId = body.get("tenantId");
        if (userId == null || userId.isBlank() || tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId and tenantId are required"));
        }

        var existing = sessionStore.findActiveSession(userId, tenantId);
        if (existing.isPresent()) {
            log.debug("Resuming active session sessionId={} userId={}", existing.get().sessionId(), userId);
            return ResponseEntity.ok(toMap(existing.get(), "resumed"));
        }

        var session = new CognitiveSession(UUID.randomUUID(), userId, tenantId,
                List.of(), "NEUTRAL", 0.5, Instant.now(), Instant.now());
        sessionStore.save(session);
        log.info("Created session sessionId={} userId={} tenantId={}", session.sessionId(), userId, tenantId);
        return ResponseEntity.status(201).body(toMap(session, "created"));
    }

    /**
     * Adds turn summaries to an existing session and updates emotional state and engagement score.
     */
    @PutMapping("/{sessionId}/turns")
    public ResponseEntity<Map<String, Object>> addTurns(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, Object> body) {

        var sessionOpt = sessionStore.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        @SuppressWarnings("unchecked")
        List<String> newSummaries = (List<String>) body.getOrDefault("turnSummaries", List.of());
        String emotionalState = (String) body.getOrDefault("emotionalState", sessionOpt.get().emotionalState());
        double engagementScore = body.containsKey("engagementScore")
                ? ((Number) body.get("engagementScore")).doubleValue()
                : sessionOpt.get().engagementScore();

        var existing = sessionOpt.get();
        var combined = new ArrayList<>(existing.turnSummaries());
        combined.addAll(newSummaries);

        var updated = new CognitiveSession(
                existing.sessionId(), existing.userId(), existing.tenantId(),
                combined, emotionalState, engagementScore,
                existing.startedAt(), Instant.now());
        sessionStore.save(updated);

        log.debug("Updated session sessionId={} turns={} emotionalState={}",
                sessionId, combined.size(), emotionalState);
        return ResponseEntity.ok(toMap(updated, "updated"));
    }

    /**
     * Closes an active session.
     */
    @PostMapping("/{sessionId}/close")
    public ResponseEntity<Map<String, Object>> close(@PathVariable UUID sessionId) {
        if (sessionStore.findById(sessionId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        sessionStore.close(sessionId);
        log.info("Closed session sessionId={}", sessionId);
        return ResponseEntity.ok(Map.of("sessionId", sessionId.toString(), "status", "closed"));
    }

    /**
     * Returns recent sessions for a user.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getRecentSessions(
            @RequestParam String userId,
            @RequestParam(defaultValue = "10") int limit) {
        var sessions = sessionStore.findRecentByUser(userId, limit);
        var body = sessions.stream().map(s -> toMap(s, "found")).toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Returns a single session by ID.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable UUID sessionId) {
        return sessionStore.findById(sessionId)
                .map(s -> ResponseEntity.ok(toMap(s, "found")))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static Map<String, Object> toMap(CognitiveSession session, String status) {
        return Map.of(
                "sessionId", session.sessionId().toString(),
                "userId", session.userId(),
                "tenantId", session.tenantId(),
                "turnSummaries", session.turnSummaries(),
                "emotionalState", session.emotionalState(),
                "engagementScore", session.engagementScore(),
                "startedAt", session.startedAt().toString(),
                "lastActiveAt", session.lastActiveAt().toString(),
                "status", status
        );
    }
}
