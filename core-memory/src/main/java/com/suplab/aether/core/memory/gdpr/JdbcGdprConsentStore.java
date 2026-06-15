package com.suplab.aether.core.memory.gdpr;

import com.suplab.aether.core.domain.GdprConsent;
import com.suplab.aether.core.ports.GdprConsentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link GdprConsentStore}.
 *
 * <p>Consent records are keyed by (user_id, tenant_id) with upsert semantics.
 * Updating consent always updates {@code updated_at} to the current timestamp.</p>
 */
public class JdbcGdprConsentStore implements GdprConsentStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcGdprConsentStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcGdprConsentStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(GdprConsent consent) {
        var sql = """
                INSERT INTO gdpr_consent
                    (user_id, tenant_id, memory_storage_allowed, data_retention_days,
                     consent_recorded_at, updated_at)
                VALUES
                    (:userId, :tenantId, :memoryStorageAllowed, :dataRetentionDays,
                     :consentRecordedAt, :updatedAt)
                ON CONFLICT (user_id, tenant_id) DO UPDATE SET
                    memory_storage_allowed = EXCLUDED.memory_storage_allowed,
                    data_retention_days    = EXCLUDED.data_retention_days,
                    updated_at             = EXCLUDED.updated_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", consent.userId())
                .addValue("tenantId", consent.tenantId())
                .addValue("memoryStorageAllowed", consent.memoryStorageAllowed())
                .addValue("dataRetentionDays", consent.dataRetentionDays())
                .addValue("consentRecordedAt", Timestamp.from(consent.consentRecordedAt()))
                .addValue("updatedAt", Timestamp.from(consent.updatedAt()));
        jdbc.update(sql, params);
        log.info("GDPR consent updated userId={} tenantId={} allowed={}",
                consent.userId(), consent.tenantId(), consent.memoryStorageAllowed());
    }

    @Override
    public Optional<GdprConsent> findByUser(String userId, String tenantId) {
        var sql = """
                SELECT user_id, tenant_id, memory_storage_allowed, data_retention_days,
                       consent_recorded_at, updated_at
                FROM gdpr_consent
                WHERE user_id = :userId AND tenant_id = :tenantId
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tenantId", tenantId);
        List<GdprConsent> results = jdbc.query(sql, params, this::mapRow);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private GdprConsent mapRow(ResultSet rs, int row) throws SQLException {
        return new GdprConsent(
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                rs.getBoolean("memory_storage_allowed"),
                rs.getInt("data_retention_days"),
                rs.getTimestamp("consent_recorded_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
