package com.suplab.aether.core.memory.store;

import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * pgvector-backed implementation of {@link PersonalMemoryStore}.
 *
 * <p>Vector embeddings are stored in a {@code vector(384)} column using the pgvector
 * extension. The {@code <=>} operator provides cosine distance ordering for semantic
 * similarity search. Embeddings are serialised as {@code [x,y,z,...]} strings and cast
 * to {@code ::vector} in the SQL, which pgvector parses at query time.</p>
 */
public class PGVectorPersonalMemoryStore implements PersonalMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(PGVectorPersonalMemoryStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public PGVectorPersonalMemoryStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(PersonalMemory memory, float[] embedding) {
        var sql = """
                INSERT INTO personal_memories
                    (id, user_id, memory_type, content, embedding, strength, access_count,
                     created_at, last_accessed_at)
                VALUES
                    (:id, :userId, :memoryType, :content, :embedding::vector, :strength,
                     :accessCount, :createdAt, :lastAccessedAt)
                ON CONFLICT (id) DO UPDATE SET
                    content = EXCLUDED.content,
                    strength = EXCLUDED.strength,
                    access_count = EXCLUDED.access_count,
                    last_accessed_at = EXCLUDED.last_accessed_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", memory.id())
                .addValue("userId", memory.userId())
                .addValue("memoryType", memory.type().name())
                .addValue("content", memory.content())
                .addValue("embedding", toVectorString(embedding))
                .addValue("strength", memory.strength())
                .addValue("accessCount", memory.accessCount())
                .addValue("createdAt", Timestamp.from(memory.createdAt()))
                .addValue("lastAccessedAt", Timestamp.from(memory.lastAccessedAt()));
        jdbc.update(sql, params);
        log.debug("Saved personal memory id={} userId={} type={}", memory.id(), memory.userId(), memory.type());
    }

    @Override
    public List<PersonalMemory> findSimilar(String userId, float[] queryEmbedding, int limit) {
        var sql = """
                SELECT id, user_id, memory_type, content, strength, access_count,
                       created_at, last_accessed_at
                FROM personal_memories
                WHERE user_id = :userId
                ORDER BY embedding <=> :query::vector
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("query", toVectorString(queryEmbedding))
                .addValue("limit", limit);
        var memories = jdbc.query(sql, params, this::mapRow);
        return memories.stream().map(this::reinforceAndPersist).toList();
    }

    @Override
    public List<PersonalMemory> findByType(String userId, MemoryType type, int limit) {
        var sql = """
                SELECT id, user_id, memory_type, content, strength, access_count,
                       created_at, last_accessed_at
                FROM personal_memories
                WHERE user_id = :userId AND memory_type = :memoryType
                ORDER BY strength DESC, last_accessed_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("memoryType", type.name())
                .addValue("limit", limit);
        var memories = jdbc.query(sql, params, this::mapRow);
        return memories.stream().map(this::reinforceAndPersist).toList();
    }

    private PersonalMemory reinforceAndPersist(PersonalMemory memory) {
        var reinforced = memory.reinforce();
        var sql = """
                UPDATE personal_memories
                SET strength = :strength, access_count = :accessCount,
                    last_accessed_at = :lastAccessedAt
                WHERE id = :id AND user_id = :userId
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", reinforced.id())
                .addValue("userId", reinforced.userId())
                .addValue("strength", reinforced.strength())
                .addValue("accessCount", reinforced.accessCount())
                .addValue("lastAccessedAt", Timestamp.from(reinforced.lastAccessedAt()));
        jdbc.update(sql, params);
        log.debug("Reinforced memory id={} strength={}", reinforced.id(), reinforced.strength());
        return reinforced;
    }

    private PersonalMemory mapRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new PersonalMemory(
                UUID.fromString(rs.getString("id")),
                rs.getString("user_id"),
                MemoryType.valueOf(rs.getString("memory_type")),
                rs.getString("content"),
                rs.getDouble("strength"),
                rs.getInt("access_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("last_accessed_at").toInstant()
        );
    }

    @Override
    public void delete(UUID memoryId, String userId) {
        var sql = "DELETE FROM personal_memories WHERE id = :id AND user_id = :userId";
        var params = new MapSqlParameterSource()
                .addValue("id", memoryId)
                .addValue("userId", userId);
        int deleted = jdbc.update(sql, params);
        log.debug("Deleted {} personal memory record(s) memoryId={} userId={}", deleted, memoryId, userId);
    }

    @Override
    public long countByUser(String userId) {
        var sql = "SELECT COUNT(*) FROM personal_memories WHERE user_id = :userId";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource("userId", userId), Long.class);
        return count != null ? count : 0L;
    }

    /**
     * Converts a float array to the {@code [x,y,z,...]} string format expected by pgvector's
     * {@code ::vector} cast operator.
     */
    static String toVectorString(float[] vec) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }
}
