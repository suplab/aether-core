package com.suplab.aether.core.api.config;

import com.suplab.aether.core.memory.context.DefaultPersonalContextProvider;
import com.suplab.aether.core.memory.embedding.PersonalEmbeddingService;
import com.suplab.aether.core.memory.store.PGVectorPersonalMemoryStore;
import com.suplab.aether.core.ports.PersonalContextProvider;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Spring configuration for Aether Core API beans.
 *
 * <p>Wires the pgvector memory store, Ollama embedding service, and the personal context
 * provider using constructor injection. All beans are declared here — never via field
 * {@code @Autowired}.</p>
 */
@Configuration
public class CoreApiConfig {

    /**
     * Creates the personal memory store backed by pgvector.
     */
    @Bean
    public PersonalMemoryStore personalMemoryStore(NamedParameterJdbcTemplate jdbc) {
        return new PGVectorPersonalMemoryStore(jdbc);
    }

    /**
     * Creates the context provider that assembles personal context snapshots for Grid.
     *
     * @param memoryStore       the store to retrieve memories from
     * @param defaultMemoryLimit max memories per type fetched per context request
     */
    @Bean
    public PersonalContextProvider personalContextProvider(
            PersonalMemoryStore memoryStore,
            @Value("${aether.core.context.memory-limit:5}") int defaultMemoryLimit) {
        return new DefaultPersonalContextProvider(memoryStore, defaultMemoryLimit);
    }

    /**
     * Creates the embedding service that calls Ollama's {@code /api/embeddings} endpoint.
     *
     * <p>Conditional on {@code aether.core.embedding.enabled=true} (default). Set to
     * {@code false} in environments where Ollama is unavailable — memories will be saved
     * with zero vectors and semantic similarity search will be non-functional, but all
     * other endpoints remain operational.</p>
     */
    @Bean
    @ConditionalOnProperty(name = "aether.core.embedding.enabled", havingValue = "true", matchIfMissing = true)
    public PersonalEmbeddingService personalEmbeddingService(
            @Value("${aether.core.ollama.base-url:http://localhost:11434}") String ollamaUrl,
            @Value("${aether.core.embedding.model:all-minilm}") String model) {
        return new PersonalEmbeddingService(ollamaUrl, model);
    }
}
