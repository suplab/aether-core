# ── Build Stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /workspace

# Copy parent POM and module POMs first (layer-cache friendly)
COPY pom.xml .
COPY core-domain/pom.xml core-domain/
COPY core-memory/pom.xml core-memory/
COPY core-api/pom.xml core-api/
COPY core-infra/pom.xml core-infra/

# Resolve dependencies independently of source (cached layer)
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
RUN mvn -B dependency:go-offline -q

# Copy source and build
COPY core-domain/src core-domain/src
COPY core-memory/src core-memory/src
COPY core-api/src core-api/src

RUN mvn -B clean package -pl core-api -am -DskipTests \
    --no-transfer-progress

# ── Runtime Stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-noble

# Create non-root user (uid 1000)
RUN addgroup --system --gid 1000 aether && \
    adduser  --system --uid 1000 --ingroup aether --no-create-home aether

WORKDIR /app

# Copy the fat jar from the build stage
COPY --from=build /workspace/core-api/target/core-api-*.jar app.jar

# Ensure non-root owns the workdir
RUN chown -R aether:aether /app
USER aether

# Expose application port
EXPOSE 8082

# Health check for container orchestrators
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8082/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
    "--enable-preview", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
