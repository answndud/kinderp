FROM eclipse-temurin:21-jdk-jammy@sha256:9d8dcf999b0bce2453e913823595a5ff2a4e8e9e5d5241b45280d0ff069818ec AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

RUN chmod +x gradlew
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy@sha256:d63bd8d9b171999cbed8576f2c76e874dd4856791a358536e5c4d407e77edc13

WORKDIR /app

RUN groupadd --system --gid 10001 erp \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin erp \
    && mkdir -p /app/logs \
    && chown -R erp:erp /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
EXPOSE 9091

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:9091/actuator/health/readiness || exit 1

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
