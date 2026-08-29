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

# Every ceiling below is a share of the same 512 MB, and the JVM will promise more
# than the container has if each one is set to a figure that looks reasonable on its
# own. Nothing inside the JVM notices; the platform kills the container from outside
# when the total is exceeded, which reads in the log as a graceful shutdown arriving
# out of nowhere.
#
#   heap        40%  =  206 MB
#   metaspace          128 MB
#   code cache          48 MB
#   direct              32 MB   the HTTP client buffers, eleven connectors wide
#                     --------
#                      414 MB   leaving ~100 MB for thread stacks and the JVM itself
#
# The previous values promised 358 MB of heap and 160 MB of metaspace — 518 MB before
# a single thread stack, and fine right up until a run filled the heap it had been
# given. Production was killed mid-run twice on 29-08-2026, each time seconds after a
# stage completed and each time taking the run with it. The platform reports the reason
# in its own events and never in the service log, so read memory as why these numbers
# changed rather than as a proven post-mortem of those two deaths.
#
# SerialGC has the smallest footprint for a single-core free instance.
# ExitOnOutOfMemoryError makes heap exhaustion a restart with a reason in the log
# rather than a JVM that thrashes and stops answering the health check.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=40 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://localhost:${PORT:-8080}/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
