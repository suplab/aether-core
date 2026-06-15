package com.suplab.aether.core.memory.context;

import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultPersonalContextProviderTest {

    @Mock
    private PersonalMemoryStore memoryStore;

    private DefaultPersonalContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultPersonalContextProvider(memoryStore, 5);
    }

    @Test
    void buildContext_returnsEmptyWhenNoMemories() {
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EPISODIC), anyInt())).thenReturn(List.of());
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.SEMANTIC), anyInt())).thenReturn(List.of());
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EMOTIONAL), anyInt())).thenReturn(List.of());

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isEmpty();
    }

    @Test
    void buildContext_includesEpisodicAndSemanticInSummaries() {
        var episodic = memory("user-1", MemoryType.EPISODIC, "Presented Q3 roadmap");
        var semantic  = memory("user-1", MemoryType.SEMANTIC,  "Prefers async communication");

        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EPISODIC), anyInt())).thenReturn(List.of(episodic));
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.SEMANTIC),  anyInt())).thenReturn(List.of(semantic));
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EMOTIONAL), anyInt())).thenReturn(List.of());

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().recentMemorySummaries())
                .containsExactly("Presented Q3 roadmap", "Prefers async communication");
    }

    @Test
    void buildContext_setsEmotionalStateFromFirstEmotionalMemory() {
        var emotional = memory("user-1", MemoryType.EMOTIONAL, "motivated");

        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EPISODIC), anyInt())).thenReturn(List.of());
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.SEMANTIC),  anyInt())).thenReturn(List.of());
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EMOTIONAL), anyInt())).thenReturn(List.of(emotional));

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().emotionalState()).isEqualTo("MOTIVATED");
    }

    @Test
    void buildContext_defaultsToNeutralWithNoEmotionalMemories() {
        var episodic = memory("user-1", MemoryType.EPISODIC, "some event");

        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EPISODIC), anyInt())).thenReturn(List.of(episodic));
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.SEMANTIC),  anyInt())).thenReturn(List.of());
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EMOTIONAL), anyInt())).thenReturn(List.of());

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().emotionalState()).isEqualTo("NEUTRAL");
    }

    @Test
    void buildContext_computesEngagementScoreFromEpisodicStrengths() {
        var e1 = memoryWithStrength("user-1", MemoryType.EPISODIC, "event A", 0.8);
        var e2 = memoryWithStrength("user-1", MemoryType.EPISODIC, "event B", 0.6);

        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EPISODIC), anyInt())).thenReturn(List.of(e1, e2));
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.SEMANTIC),  anyInt())).thenReturn(List.of());
        when(memoryStore.findByType(eq("user-1"), eq(MemoryType.EMOTIONAL), anyInt())).thenReturn(List.of());

        var result = provider.buildContext("acme", "user-1");

        assertThat(result).isPresent();
        assertThat(result.get().engagementScore()).isCloseTo(0.7, within(0.001));
    }

    @Test
    void buildContext_populatesUserAndTenantId() {
        var episodic = memory("user-42", MemoryType.EPISODIC, "meeting notes");

        when(memoryStore.findByType(eq("user-42"), eq(MemoryType.EPISODIC), anyInt())).thenReturn(List.of(episodic));
        when(memoryStore.findByType(eq("user-42"), eq(MemoryType.SEMANTIC),  anyInt())).thenReturn(List.of());
        when(memoryStore.findByType(eq("user-42"), eq(MemoryType.EMOTIONAL), anyInt())).thenReturn(List.of());

        var result = provider.buildContext("corp-tenant", "user-42");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("user-42");
        assertThat(result.get().tenantId()).isEqualTo("corp-tenant");
    }

    private static PersonalMemory memory(String userId, MemoryType type, String content) {
        return memoryWithStrength(userId, type, content, 1.0);
    }

    private static PersonalMemory memoryWithStrength(String userId, MemoryType type, String content, double strength) {
        return new PersonalMemory(UUID.randomUUID(), userId, type, content,
                strength, 0, Instant.now(), Instant.now());
    }
}
