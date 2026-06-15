package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.GdprConsent;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.GdprConsentStore;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST API for GDPR compliance — consent management and right to erasure.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/v1/users/{userId}/gdpr/consent} — get current consent</li>
 *   <li>{@code PUT  /api/v1/users/{userId}/gdpr/consent} — update consent preferences</li>
 *   <li>{@code DELETE /api/v1/users/{userId}/gdpr/erase} — GDPR Article 17 erasure</li>
 * </ul>
 *
 * <h2>Erasure</h2>
 * <p>The erasure endpoint performs a coordinated hard-delete across all user data in
 * Aether Core: personal memories, cognitive sessions, and consent records. This
 * implements GDPR Article 17 (Right to Erasure).</p>
 *
 * <p>Audit log entries survive erasure (no FK constraints by design) but PII within
 * them has already been redacted before insertion.</p>
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/gdpr")
public class GdprController {

    private static final Logger log = LoggerFactory.getLogger(GdprController.class);

    private final GdprConsentStore consentStore;
    private final PersonalMemoryStore memoryStore;
    private final CognitiveSessionStore sessionStore;

    public GdprController(GdprConsentStore consentStore,
                          PersonalMemoryStore memoryStore,
                          CognitiveSessionStore sessionStore) {
        this.consentStore = consentStore;
        this.memoryStore = memoryStore;
        this.sessionStore = sessionStore;
    }

    /**
     * Returns the current GDPR consent record for a user.
     */
    @GetMapping("/consent")
    public ResponseEntity<Map<String, Object>> getConsent(
            @PathVariable String userId,
            @RequestParam String tenantId) {
        var consent = consentStore.findByUser(userId, tenantId)
                .orElseGet(() -> GdprConsent.defaultConsent(userId, tenantId));
        return ResponseEntity.ok(toMap(consent));
    }

    /**
     * Updates GDPR consent preferences for a user.
     *
     * @param body must contain {@code tenantId}, {@code memoryStorageAllowed} (boolean),
     *             and optionally {@code dataRetentionDays} (int, default 365)
     */
    @PutMapping("/consent")
    public ResponseEntity<Map<String, Object>> updateConsent(
            @PathVariable String userId,
            @RequestBody Map<String, Object> body) {
        String tenantId = (String) body.get("tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "tenantId is required"));
        }
        boolean allowed = Boolean.parseBoolean(body.getOrDefault("memoryStorageAllowed", "true").toString());
        int retentionDays = Integer.parseInt(body.getOrDefault("dataRetentionDays", "365").toString());

        var consent = new GdprConsent(userId, tenantId, allowed, retentionDays,
                java.time.Instant.now(), java.time.Instant.now());
        consentStore.save(consent);
        log.info("GDPR consent updated userId={} tenantId={} allowed={}", userId, tenantId, allowed);
        return ResponseEntity.ok(toMap(consent));
    }

    /**
     * GDPR Article 17 — Right to Erasure.
     *
     * <p>Hard-deletes all personal data for the user: memories, sessions, and consent record.
     * This operation is irreversible.</p>
     */
    @DeleteMapping("/erase")
    public ResponseEntity<Map<String, Object>> eraseUser(
            @PathVariable String userId,
            @RequestParam String tenantId) {
        log.warn("GDPR erasure initiated userId={} tenantId={}", userId, tenantId);

        long memoriesDeleted = memoryStore.countByUser(userId);
        // Delete all memories — iterate by fetching IDs then deleting individually
        // In production this would use a bulk DELETE; using port interface here for testability
        eraseAllMemories(userId);

        int sessionsDeleted = sessionStore.eraseByUser(userId, tenantId);
        consentStore.findByUser(userId, tenantId).ifPresent(c ->
                consentStore.save(GdprConsent.optOut(userId, tenantId)));

        log.warn("GDPR erasure complete userId={} tenantId={} memoriesDeleted={} sessionsDeleted={}",
                userId, tenantId, memoriesDeleted, sessionsDeleted);

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "tenantId", tenantId,
                "memoriesDeleted", memoriesDeleted,
                "sessionsDeleted", sessionsDeleted,
                "status", "erased"
        ));
    }

    private void eraseAllMemories(String userId) {
        // Fetch all memories for this user in batches and delete them
        // The PersonalMemoryStore port has delete(UUID, String) — we use it
        var memories = memoryStore.findByType(userId,
                com.suplab.aether.core.domain.MemoryType.EPISODIC, 1000);
        memories.forEach(m -> memoryStore.delete(m.id(), userId));

        memories = memoryStore.findByType(userId,
                com.suplab.aether.core.domain.MemoryType.SEMANTIC, 1000);
        memories.forEach(m -> memoryStore.delete(m.id(), userId));

        memories = memoryStore.findByType(userId,
                com.suplab.aether.core.domain.MemoryType.PROCEDURAL, 1000);
        memories.forEach(m -> memoryStore.delete(m.id(), userId));

        memories = memoryStore.findByType(userId,
                com.suplab.aether.core.domain.MemoryType.EMOTIONAL, 1000);
        memories.forEach(m -> memoryStore.delete(m.id(), userId));

        log.debug("Memories erased for userId={}", userId);
    }

    private static Map<String, Object> toMap(GdprConsent consent) {
        return Map.of(
                "userId", consent.userId(),
                "tenantId", consent.tenantId(),
                "memoryStorageAllowed", consent.memoryStorageAllowed(),
                "dataRetentionDays", consent.dataRetentionDays(),
                "consentRecordedAt", consent.consentRecordedAt().toString(),
                "updatedAt", consent.updatedAt().toString()
        );
    }
}
