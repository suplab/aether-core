package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;

import java.util.List;
import java.util.UUID;

/**
 * Port interface for personal memory persistence.
 *
 * <p>Implementations are in {@code core-memory} (pgvector) and test stubs. The domain
 * layer never depends on any concrete implementation — only this interface.</p>
 */
public interface PersonalMemoryStore {

    /**
     * Persists a personal memory alongside its vector embedding.
     * Uses UPSERT semantics — calling with an existing ID updates the record.
     *
     * @param memory    the personal memory to persist
     * @param embedding the 384-dimension embedding vector for semantic search
     */
    void save(PersonalMemory memory, float[] embedding);

    /**
     * Returns the {@code limit} memories most semantically similar to the query embedding,
     * ordered by cosine distance ascending.
     *
     * @param userId         the user whose memories to search
     * @param queryEmbedding the 384-dimension query vector
     * @param limit          maximum number of results to return
     * @return ordered list of similar memories (nearest first)
     */
    List<PersonalMemory> findSimilar(String userId, float[] queryEmbedding, int limit);

    /**
     * Returns memories of a specific type for a user, ordered by strength descending,
     * then by last access time descending.
     *
     * @param userId the user whose memories to retrieve
     * @param type   the memory type filter
     * @param limit  maximum number of results to return
     * @return ordered list of memories matching the type
     */
    List<PersonalMemory> findByType(String userId, MemoryType type, int limit);

    /**
     * Hard-deletes a specific memory. The userId is required to prevent cross-user deletion.
     *
     * @param memoryId the UUID of the memory to delete
     * @param userId   the owner of the memory (enforces scoping)
     */
    void delete(UUID memoryId, String userId);

    /**
     * Returns the total number of memories stored for a user.
     *
     * @param userId the user to count for
     * @return non-negative memory count
     */
    long countByUser(String userId);
}
