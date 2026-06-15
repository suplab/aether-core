package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.memory.embedding.PersonalEmbeddingService;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD operations for personal memories.
 *
 * <p>On write ({@code POST}), content is embedded via Ollama and stored alongside the
 * 384-dimension vector for future semantic similarity retrieval. When the embedding service
 * is disabled ({@code aether.core.embedding.enabled=false}), a zero vector is stored and
 * the memory is still persisted — semantic search will be non-functional until embeddings
 * are re-generated.</p>
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/memories")
public class PersonalMemoryController {

    private static final Logger log = LoggerFactory.getLogger(PersonalMemoryController.class);

    private final PersonalMemoryStore memoryStore;
    private final Optional<PersonalEmbeddingService> embeddingService;

    public PersonalMemoryController(PersonalMemoryStore memoryStore,
                                    Optional<PersonalEmbeddingService> embeddingService) {
        this.memoryStore = memoryStore;
        this.embeddingService = embeddingService;
    }

    /**
     * Stores a new personal memory and its embedding vector.
     *
     * <p>Request body: {@code {"type": "EPISODIC", "content": "..."}}.
     * Type defaults to {@code EPISODIC} if omitted.</p>
     *
     * @param userId the user to store the memory for
     * @param body   JSON map with {@code type} and {@code content} fields
     * @return 201 Created with {@code memoryId} and {@code type}, or 400 if content is missing
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> store(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {

        var content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "content is required"));
        }

        MemoryType type;
        try {
            type = MemoryType.valueOf(body.getOrDefault("type", "EPISODIC").toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid type; valid values: EPISODIC, SEMANTIC, PROCEDURAL, EMOTIONAL"));
        }

        var memory = PersonalMemory.create(userId, type, content);
        var embedding = embeddingService.map(svc -> svc.embed(content)).orElseGet(() -> new float[384]);
        memoryStore.save(memory, embedding);

        log.info("Stored {} memory id={} userId={} embeddingEnabled={}", type, memory.id(), userId,
                embeddingService.isPresent());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("memoryId", memory.id().toString(), "type", type.name()));
    }

    /**
     * Returns the total count of memories stored for the user.
     *
     * @param userId the user to count for
     * @return 200 OK with {@code userId} and {@code count}
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> count(@PathVariable String userId) {
        long count = memoryStore.countByUser(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "count", count));
    }

    /**
     * Deletes a specific memory. The userId scoping prevents cross-user deletion.
     *
     * @param userId   the owner of the memory
     * @param memoryId the UUID of the memory to delete
     * @return 204 No Content
     */
    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> delete(@PathVariable String userId, @PathVariable UUID memoryId) {
        memoryStore.delete(memoryId, userId);
        log.info("Deleted memory memoryId={} userId={}", memoryId, userId);
        return ResponseEntity.noContent().build();
    }
}
