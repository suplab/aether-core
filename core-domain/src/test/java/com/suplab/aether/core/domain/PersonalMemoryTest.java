package com.suplab.aether.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PersonalMemoryTest {

    @Test
    void create_setsInitialStrengthToOne() {
        var memory = PersonalMemory.create("user-1", MemoryType.EPISODIC, "Attended Q3 planning");

        assertThat(memory.strength()).isEqualTo(1.0);
        assertThat(memory.accessCount()).isZero();
        assertThat(memory.userId()).isEqualTo("user-1");
        assertThat(memory.type()).isEqualTo(MemoryType.EPISODIC);
        assertThat(memory.content()).isEqualTo("Attended Q3 planning");
        assertThat(memory.id()).isNotNull();
    }

    @Test
    void create_assignsDistinctIdsForEachCall() {
        var a = PersonalMemory.create("user-1", MemoryType.SEMANTIC, "prefers async");
        var b = PersonalMemory.create("user-1", MemoryType.SEMANTIC, "prefers async");

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void reinforce_incrementsStrengthByPointOne() {
        var memory = PersonalMemory.create("user-1", MemoryType.EPISODIC, "Launched feature X");
        var reinforced = memory.reinforce();

        assertThat(reinforced.strength()).isEqualTo(1.0); // already capped at 1.0
        assertThat(reinforced.accessCount()).isEqualTo(1);
    }

    @Test
    void reinforce_incrementsFromLowStrength() {
        var memory = new PersonalMemory(UUID.randomUUID(), "user-1", MemoryType.EMOTIONAL, "excited",
                0.5, 3, Instant.now(), Instant.now());
        var reinforced = memory.reinforce();

        assertThat(reinforced.strength()).isCloseTo(0.6, within(0.001));
        assertThat(reinforced.accessCount()).isEqualTo(4);
    }

    @Test
    void reinforce_capsStrengthAtOne() {
        var memory = new PersonalMemory(UUID.randomUUID(), "user-1", MemoryType.PROCEDURAL, "deploy process",
                0.95, 10, Instant.now(), Instant.now());
        var reinforced = memory.reinforce();

        assertThat(reinforced.strength()).isEqualTo(1.0);
    }

    @Test
    void reinforce_preservesIdentityFields() {
        var id = UUID.randomUUID();
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        var memory = new PersonalMemory(id, "user-1", MemoryType.SEMANTIC, "Java expert",
                0.7, 5, createdAt, Instant.now());

        var reinforced = memory.reinforce();

        assertThat(reinforced.id()).isEqualTo(id);
        assertThat(reinforced.userId()).isEqualTo("user-1");
        assertThat(reinforced.type()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(reinforced.content()).isEqualTo("Java expert");
        assertThat(reinforced.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void reinforce_updatesLastAccessedAt() {
        var before = Instant.now().minusSeconds(60);
        var memory = new PersonalMemory(UUID.randomUUID(), "user-1", MemoryType.EPISODIC, "past event",
                0.5, 0, before, before);

        var reinforced = memory.reinforce();

        assertThat(reinforced.lastAccessedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void constructor_rejectsBlankUserId() {
        assertThatThrownBy(() -> new PersonalMemory(UUID.randomUUID(), "", MemoryType.EPISODIC, "content",
                0.5, 0, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId required");
    }

    @Test
    void constructor_rejectsNullType() {
        assertThatThrownBy(() -> new PersonalMemory(UUID.randomUUID(), "user-1", null, "content",
                0.5, 0, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type required");
    }

    @Test
    void constructor_rejectsBlankContent() {
        assertThatThrownBy(() -> new PersonalMemory(UUID.randomUUID(), "user-1", MemoryType.EPISODIC, "  ",
                0.5, 0, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content required");
    }

    @Test
    void constructor_rejectsStrengthOutOfRange() {
        assertThatThrownBy(() -> new PersonalMemory(UUID.randomUUID(), "user-1", MemoryType.EPISODIC, "content",
                1.5, 0, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strength must be 0-1");

        assertThatThrownBy(() -> new PersonalMemory(UUID.randomUUID(), "user-1", MemoryType.EPISODIC, "content",
                -0.1, 0, Instant.now(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strength must be 0-1");
    }

    @Test
    void allMemoryTypes_areValid() {
        for (var type : MemoryType.values()) {
            var memory = PersonalMemory.create("user-1", type, "test content");
            assertThat(memory.type()).isEqualTo(type);
        }
    }
}
