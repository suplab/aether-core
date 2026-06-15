package com.suplab.aether.core.memory.decay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled service that applies exponential decay to personal memories.
 *
 * <h2>Decay Model</h2>
 * <p>Memories that have not been accessed for {@code idleThresholdDays} days lose
 * {@code decayFactor} (default 5%) of their current strength per scheduled run (daily).
 * This mirrors the Ebbinghaus forgetting curve at a simplified level.</p>
 *
 * <p>Memories whose strength falls below {@code purgeThreshold} (default 0.05) are
 * hard-deleted in a weekly purge pass.</p>
 *
 * <h2>Reinforcement</h2>
 * <p>Reinforcement is handled inline at read time by {@link
 * com.suplab.aether.core.memory.store.PGVectorPersonalMemoryStore} — every retrieval
 * increases strength by +0.1. This service only handles decay.</p>
 */
@Service
public class MemoryDecayService {

    private static final Logger log = LoggerFactory.getLogger(MemoryDecayService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final double decayFactor;
    private final double purgeThreshold;
    private final int idleThresholdDays;

    public MemoryDecayService(
            NamedParameterJdbcTemplate jdbc,
            @Value("${aether.core.memory.decay.factor:0.05}") double decayFactor,
            @Value("${aether.core.memory.decay.purge-threshold:0.05}") double purgeThreshold,
            @Value("${aether.core.memory.decay.idle-threshold-days:7}") int idleThresholdDays) {
        this.jdbc = jdbc;
        this.decayFactor = decayFactor;
        this.purgeThreshold = purgeThreshold;
        this.idleThresholdDays = idleThresholdDays;
    }

    /**
     * Daily decay pass — reduces strength of idle memories by {@code decayFactor}.
     * Runs at 02:00 UTC every day.
     */
    @Scheduled(cron = "${aether.core.memory.decay.cron:0 0 2 * * *}")
    @Transactional
    public void applyDailyDecay() {
        var sql = """
                UPDATE personal_memories
                SET strength = GREATEST(0.0, strength * (1.0 - :decayFactor)),
                    last_accessed_at = last_accessed_at
                WHERE last_accessed_at < NOW() - MAKE_INTERVAL(days => :idleThresholdDays)
                  AND strength > :purgeThreshold
                """;
        var params = new MapSqlParameterSource()
                .addValue("decayFactor", decayFactor)
                .addValue("idleThresholdDays", idleThresholdDays)
                .addValue("purgeThreshold", purgeThreshold);
        int updated = jdbc.update(sql, params);
        log.info("Memory decay applied to {} memories (decayFactor={} idleThreshold={}d)",
                updated, decayFactor, idleThresholdDays);
    }

    /**
     * Weekly purge pass — hard-deletes memories below {@code purgeThreshold}.
     * Runs at 03:00 UTC every Sunday.
     */
    @Scheduled(cron = "${aether.core.memory.decay.purge-cron:0 0 3 * * SUN}")
    @Transactional
    public void purgeWeakMemories() {
        var sql = "DELETE FROM personal_memories WHERE strength <= :purgeThreshold";
        var params = new MapSqlParameterSource("purgeThreshold", purgeThreshold);
        int deleted = jdbc.update(sql, params);
        log.info("Memory purge: deleted {} memories below strength threshold {}",
                deleted, purgeThreshold);
    }

    /**
     * Closes sessions that have been inactive for more than {@code sessionTimeoutDays} days.
     * Runs weekly alongside the memory purge.
     */
    @Scheduled(cron = "${aether.core.memory.decay.purge-cron:0 0 3 * * SUN}")
    @Transactional
    public void expireInactiveSessions() {
        var sql = """
                UPDATE cognitive_sessions
                SET active = false
                WHERE active = true
                  AND last_active_at < NOW() - INTERVAL '7 days'
                """;
        int expired = jdbc.update(sql, new MapSqlParameterSource());
        log.info("Expired {} inactive sessions", expired);
    }
}
