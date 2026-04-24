# ─────────────────────────────────────────────
# Stage 1: Build — Maven + JDK 17
# ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Cache dependency layer separately
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# Build
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# ─────────────────────────────────────────────
# Stage 2: Runtime — slim JRE 17
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Non-root user for security
RUN addgroup --system floucna && adduser --system --ingroup floucna floucna

# Copy fat JAR from builder
COPY --from=builder /app/target/backend-1.0-SNAPSHOT.jar app.jar

# Persistent directories (mounted as volumes)
RUN mkdir -p contracts uploads data && chown -R floucna:floucna /app

USER floucna

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1

CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-cp", "app.jar", "com.floucna.Main"]
