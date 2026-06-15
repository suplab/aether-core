package com.suplab.aether.core.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aether Core — personal cognitive engine.
 *
 * <p>Runs on port 8082 (Grid proxy=8080, Grid api=8081, Core=8082). Provides the
 * {@code GET /api/v1/personal-context/{tenantId}/{userId}} endpoint consumed by
 * Aether Grid's {@code AetherCoreBridgeAgent}.</p>
 *
 * <p>{@code scanBasePackages} covers all sub-packages of {@code com.suplab.aether.core}
 * so beans from {@code core-memory} (embedding service, memory store) are discovered
 * via the config class in {@code core-api}.</p>
 */
@SpringBootApplication(scanBasePackages = "com.suplab.aether.core")
public class AetherCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(AetherCoreApplication.class, args);
    }
}
