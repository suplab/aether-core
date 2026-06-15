package com.suplab.aether.core.memory.store;

import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers
class PGVectorPersonalMemoryStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_core_test")
            .withUsername("aether")
            .withPassword("aether");

    private PGVectorPersonalMemoryStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        var jdbc = new NamedParameterJdbcTemplate(dataSource);
        store = new PGVectorPersonalMemoryStore(jdbc);
    }

    @Test
    void save_andFindByType_roundTrip() {
        var userId = "user-" + UUID.randomUUID();
        var memory = PersonalMemory.create(userId, MemoryType.EPISODIC, "Presented Q3 roadmap");
        float[] embedding = new float[384];

        store.save(memory, embedding);

        var found = store.findByType(userId, MemoryType.EPISODIC, 10);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().content()).isEqualTo("Presented Q3 roadmap");
        assertThat(found.getFirst().type()).isEqualTo(MemoryType.EPISODIC);
        assertThat(found.getFirst().userId()).isEqualTo(userId);
    }

    @Test
    void findByType_reinforcesMemoryOnRead() {
        var userId = "user-" + UUID.randomUUID();
        var memory = PersonalMemory.create(userId, MemoryType.SEMANTIC, "Prefers async communication");
        memory = new com.suplab.aether.core.domain.PersonalMemory(
                memory.id(), userId, MemoryType.SEMANTIC, memory.content(),
                0.5, 0, memory.createdAt(), memory.lastAccessedAt());

        store.save(memory, new float[384]);

        var afterFirstRead = store.findByType(userId, MemoryType.SEMANTIC, 10);
        assertThat(afterFirstRead).hasSize(1);
        assertThat(afterFirstRead.getFirst().strength()).isCloseTo(0.6, within(0.001));
        assertThat(afterFirstRead.getFirst().accessCount()).isEqualTo(1);

        var afterSecondRead = store.findByType(userId, MemoryType.SEMANTIC, 10);
        assertThat(afterSecondRead.getFirst().strength()).isCloseTo(0.7, within(0.001));
        assertThat(afterSecondRead.getFirst().accessCount()).isEqualTo(2);
    }

    @Test
    void findSimilar_returnsReinforcedMemories() {
        var userId = "user-" + UUID.randomUUID();
        var memory = PersonalMemory.create(userId, MemoryType.EMOTIONAL, "motivated");
        store.save(memory, new float[384]);

        var found = store.findSimilar(userId, new float[384], 5);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().accessCount()).isEqualTo(1);
        assertThat(found.getFirst().strength()).isEqualTo(1.0); // already at max, stays capped
    }

    @Test
    void countByUser_returnsCorrectCount() {
        var userId = "user-" + UUID.randomUUID();
        assertThat(store.countByUser(userId)).isZero();

        store.save(PersonalMemory.create(userId, MemoryType.EPISODIC, "memory 1"), new float[384]);
        store.save(PersonalMemory.create(userId, MemoryType.SEMANTIC, "memory 2"), new float[384]);
        assertThat(store.countByUser(userId)).isEqualTo(2);
    }

    @Test
    void delete_removesMemoryForUser() {
        var userId = "user-" + UUID.randomUUID();
        var memory = PersonalMemory.create(userId, MemoryType.PROCEDURAL, "deploy process");
        store.save(memory, new float[384]);

        assertThat(store.countByUser(userId)).isEqualTo(1);

        store.delete(memory.id(), userId);

        assertThat(store.countByUser(userId)).isZero();
    }

    @Test
    void delete_doesNotDeleteOtherUsersMemory() {
        var userA = "user-" + UUID.randomUUID();
        var userB = "user-" + UUID.randomUUID();
        var memoryA = PersonalMemory.create(userA, MemoryType.EPISODIC, "user A memory");
        store.save(memoryA, new float[384]);

        store.delete(memoryA.id(), userB);

        assertThat(store.countByUser(userA)).isEqualTo(1);
    }

    @Test
    void findByType_isolatesPerUser() {
        var userA = "user-" + UUID.randomUUID();
        var userB = "user-" + UUID.randomUUID();

        store.save(PersonalMemory.create(userA, MemoryType.EPISODIC, "user A episodic"), new float[384]);
        store.save(PersonalMemory.create(userB, MemoryType.EPISODIC, "user B episodic"), new float[384]);

        var resultA = store.findByType(userA, MemoryType.EPISODIC, 10);
        assertThat(resultA).hasSize(1);
        assertThat(resultA.getFirst().content()).isEqualTo("user A episodic");
    }

    @Test
    void save_upsertUpdatesExistingRecord() {
        var userId = "user-" + UUID.randomUUID();
        var memory = PersonalMemory.create(userId, MemoryType.SEMANTIC, "original content");
        store.save(memory, new float[384]);

        var updated = new PersonalMemory(memory.id(), userId, MemoryType.SEMANTIC,
                "updated content", 0.9, 5, memory.createdAt(), memory.lastAccessedAt());
        store.save(updated, new float[384]);

        var found = store.findByType(userId, MemoryType.SEMANTIC, 10);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().content()).isEqualTo("updated content");
    }
}
