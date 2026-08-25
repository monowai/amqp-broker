# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
# The Gradle toolchain wants a JDK 25. Build and run stages are pinned to the same major.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Wrapper and build scripts first. These change far less often than source, so the dependency
# download below stays cached across ordinary code edits.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src src
# ktlint and the QPID-backed tests already ran in CI; -x test keeps image builds quick.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test

# Split the fat jar into layers that change at different rates, so a code-only change
# reuses the (large) dependency layer instead of shipping it again.
RUN mkdir -p /workspace/extracted \
 && java -Djarmode=tools -jar build/libs/*-SNAPSHOT.jar extract --layers --launcher --destination /workspace/extracted

# ---- run ------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# Never run the app as root.
RUN groupadd --system spring && useradd --system --gid spring spring
WORKDIR /app

# Ordered least- to most-frequently changed.
COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring:spring

# Where the broker lives. Override for a real RabbitMQ.
ENV SPRING_RABBITMQ_HOST=rabbitmq \
    SPRING_RABBITMQ_PORT=5672 \
    DEPLOYMENT_ENVIRONMENT=docker

# Tracing is off the shelf and vendor neutral. Set an OTLP endpoint and spans ship there;
# leave it unset and they are still created, still correlating every log line.
#   -e MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://collector:4318/v1/traces
ENV MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0

# The demo publishes a batch, then reads stdin until you type "q" - so run it with -i.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
