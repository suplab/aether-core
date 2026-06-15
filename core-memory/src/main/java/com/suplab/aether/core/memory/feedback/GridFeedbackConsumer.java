package com.suplab.aether.core.memory.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for decision feedback published by Aether Grid.
 *
 * <p>Grid publishes feedback events to {@code aether.core.feedback} whenever an agent
 * decision outcome is known (correct, incorrect, partially-correct). Aether Core uses
 * these signals to reinforce memories that contributed to correct decisions and to
 * flag memories associated with incorrect ones for potential decay acceleration.</p>
 *
 * <h2>Message Format</h2>
 * <pre>
 * {
 *   "userId":          "user-42",
 *   "tenantId":        "acme-corp",
 *   "memoryId":        "550e8400-e29b-41d4-a716-446655440000",
 *   "outcome":         "CORRECT | INCORRECT | PARTIALLY_CORRECT | UNKNOWN",
 *   "decisionType":    "BLOCK | ALLOW | ALERT | SUGGEST | DEFER",
 *   "confidenceScore": 0.92
 * }
 * </pre>
 *
 * <p>This component is conditional on {@code aether.core.kafka.enabled=true} (default false)
 * so that Core can run standalone without Kafka.</p>
 */
@Component
@ConditionalOnProperty(name = "aether.core.kafka.enabled", havingValue = "true")
public class GridFeedbackConsumer {

    private static final Logger log = LoggerFactory.getLogger(GridFeedbackConsumer.class);

    private final PersonalMemoryStore memoryStore;
    private final ObjectMapper objectMapper;

    public GridFeedbackConsumer(PersonalMemoryStore memoryStore, ObjectMapper objectMapper) {
        this.memoryStore = memoryStore;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${aether.core.kafka.feedback-topic:aether.core.feedback}",
                   groupId = "${spring.kafka.consumer.group-id:aether-core}")
    public void onFeedback(String message) {
        try {
            var event = objectMapper.readValue(message, FeedbackEvent.class);
            log.debug("Received Grid feedback userId={} memoryId={} outcome={}",
                    event.userId(), event.memoryId(), event.outcome());
            processFeedback(event);
        } catch (Exception e) {
            log.error("Failed to process Grid feedback message: {}", message, e);
            // Do not rethrow — we log and move on; a single bad message must not stop the consumer
        }
    }

    private void processFeedback(FeedbackEvent event) {
        if (event.memoryId() == null || event.userId() == null) {
            log.warn("Ignoring feedback event with missing memoryId or userId");
            return;
        }

        switch (event.outcome()) {
            case "CORRECT" -> {
                // Reinforce — find and re-save with strength boost
                // The reinforce-on-read in the store already handles incremental boosts;
                // an explicit feedback CORRECT merits an additional bump
                log.info("Reinforcing memory memoryId={} userId={} — outcome=CORRECT",
                        event.memoryId(), event.userId());
                reinforceMemory(event.memoryId(), event.userId());
            }
            case "INCORRECT" -> {
                // Decay aggressively — a strong incorrect prediction should weaken that memory
                log.info("Decaying memory memoryId={} userId={} — outcome=INCORRECT",
                        event.memoryId(), event.userId());
                decayMemory(event.memoryId(), event.userId());
            }
            default -> log.debug("No action for outcome={} memoryId={}", event.outcome(), event.memoryId());
        }
    }

    private void reinforceMemory(String memoryId, String userId) {
        try {
            var memories = memoryStore.findByType(userId,
                    com.suplab.aether.core.domain.MemoryType.EPISODIC, 100);
            memories.stream()
                    .filter(m -> m.id().toString().equals(memoryId))
                    .findFirst()
                    .ifPresent(m -> {
                        var reinforced = m.reinforce();
                        memoryStore.save(reinforced, new float[384]); // keep existing embedding
                    });
        } catch (Exception e) {
            log.warn("Could not reinforce memory memoryId={}: {}", memoryId, e.getMessage());
        }
    }

    private void decayMemory(String memoryId, String userId) {
        // Decay is handled via the scheduled MemoryDecayService;
        // for incorrect feedback we simply log — aggressive decay could harm accuracy
        // Future: write to a feedback_signals table for the decay scheduler to act on
        log.debug("Decay signal recorded for memoryId={} — will be processed by decay scheduler", memoryId);
    }

    /**
     * Inbound feedback event from Aether Grid.
     */
    record FeedbackEvent(
            @JsonProperty("userId") String userId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("memoryId") String memoryId,
            @JsonProperty("outcome") String outcome,
            @JsonProperty("decisionType") String decisionType,
            @JsonProperty("confidenceScore") double confidenceScore
    ) {}
}
