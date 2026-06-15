package com.suplab.aether.core.memory.session;

import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link CognitiveSessionStore}.
 *
 * <p>Sessions are stored in the {@code cognitive_sessions} table with an {@code active}
 * flag. Only one session per user/tenant is expected to be active at a time; this
 * invariant is enforced at the service layer, not here.</p>
 *
 * <p>Turn summaries are stored as a PostgreSQL {@code TEXT[]} array column and round-trip
 * correctly through JDBC's {@link Array} type.</p>
 */
public class JdbcCognitiveSessionStore implements CognitiveSessionStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcCognitiveSessionStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCognitiveSessionStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(CognitiveSession session) {
        var sql = """
                INSERT INTO cognitive_sessions
                    (session_id, user_id, tenant_id, turn_summaries, emotional_state,
                     engagement_score, active, started_at, last_active_at)
                VALUES
                    (:sessionId, :userId, :tenantId, :turnSummaries::text[], :emotionalState,
                     :engagementScore, :active, :startedAt, :lastActiveAt)
                ON CONFLICT (session_id) DO UPDATE SET
                    turn_summaries  = EXCLUDED.turn_summaries,
                    emotional_state = EXCLUDED.emotional_state,
                    engagement_score = EXCLUDED.engagement_score,
                    active          = EXCLUDED.active,
                    last_active_at  = EXCLUDED.last_active_at
                """;
        var params = buildParams(session, true);
        jdbc.update(sql, params);
        log.debug("Saved session sessionId={} userId={} tenantId={} active=true",
                session.sessionId(), session.userId(), session.tenantId());
    }

    @Override
    public Optional<CognitiveSession> findActiveSession(String userId, String tenantId) {
        var sql = """
                SELECT session_id, user_id, tenant_id, turn_summaries, emotional_state,
                       engagement_score, started_at, last_active_at
                FROM cognitive_sessions
                WHERE user_id = :userId AND tenant_id = :tenantId AND active = true
                ORDER BY last_active_at DESC
                LIMIT 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tenantId", tenantId);
        var results = jdbc.query(sql, params, this::mapRow);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<CognitiveSession> findById(UUID sessionId) {
        var sql = """
                SELECT session_id, user_id, tenant_id, turn_summaries, emotional_state,
                       engagement_score, started_at, last_active_at
                FROM cognitive_sessions
                WHERE session_id = :sessionId
                """;
        var params = new MapSqlParameterSource("sessionId", sessionId);
        var results = jdbc.query(sql, params, this::mapRow);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<CognitiveSession> findRecentByUser(String userId, int limit) {
        var sql = """
                SELECT session_id, user_id, tenant_id, turn_summaries, emotional_state,
                       engagement_score, started_at, last_active_at
                FROM cognitive_sessions
                WHERE user_id = :userId
                ORDER BY last_active_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public void close(UUID sessionId) {
        var sql = """
                UPDATE cognitive_sessions
                SET active = false, last_active_at = NOW()
                WHERE session_id = :sessionId
                """;
        int updated = jdbc.update(sql, new MapSqlParameterSource("sessionId", sessionId));
        log.debug("Closed {} session(s) sessionId={}", updated, sessionId);
    }

    @Override
    public int eraseByUser(String userId, String tenantId) {
        var sql = "DELETE FROM cognitive_sessions WHERE user_id = :userId AND tenant_id = :tenantId";
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tenantId", tenantId);
        int deleted = jdbc.update(sql, params);
        log.info("GDPR erasure: deleted {} session(s) userId={} tenantId={}", deleted, userId, tenantId);
        return deleted;
    }

    private CognitiveSession mapRow(ResultSet rs, int row) throws SQLException {
        List<String> summaries = List.of();
        Array arr = rs.getArray("turn_summaries");
        if (arr != null) {
            summaries = Arrays.asList((String[]) arr.getArray());
        }
        return new CognitiveSession(
                UUID.fromString(rs.getString("session_id")),
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                summaries,
                rs.getString("emotional_state"),
                rs.getDouble("engagement_score"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("last_active_at").toInstant()
        );
    }

    private MapSqlParameterSource buildParams(CognitiveSession session, boolean active) {
        String[] summariesArray = session.turnSummaries().toArray(new String[0]);
        return new MapSqlParameterSource()
                .addValue("sessionId", session.sessionId())
                .addValue("userId", session.userId())
                .addValue("tenantId", session.tenantId())
                .addValue("turnSummaries", "{" + String.join(",",
                        session.turnSummaries().stream()
                                .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                                .toList()) + "}")
                .addValue("emotionalState", session.emotionalState())
                .addValue("engagementScore", session.engagementScore())
                .addValue("active", active)
                .addValue("startedAt", Timestamp.from(session.startedAt()))
                .addValue("lastActiveAt", Timestamp.from(session.lastActiveAt()));
    }
}
