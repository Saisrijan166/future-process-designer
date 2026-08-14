# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage — uses the Maven wrapper committed to the repo, so the build does
# not depend on whatever Maven version happens to be on the host.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# Copy the wrapper and POM first so the dependency layer is cached and only
# re-downloads when the POM actually changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI against a real database; the image build stays fast and offline.
RUN ./mvnw -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Runtime stage — JRE only, non-root, tuned for a 512 MB free-tier container.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl is only here so the container-level HEALTHCHECK below has something to call.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app

COPY --from=build --chown=app:app /build/target/process-designer.jar app.jar

USER app
EXPOSE 8080

# MaxRAMPercentage keeps the heap inside the container limit; SerialGC has the
# smallest footprint for a single-core free instance.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=160m"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://localhost:${PORT:-8080}/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
