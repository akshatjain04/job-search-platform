# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/ ./backend/
RUN --mount=type=cache,target=/root/.m2 mvn -f backend/pom.xml -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends fonts-dejavu-core curl ca-certificates && rm -rf /var/lib/apt/lists/* && groupadd -g 10001 app && useradd -u 10001 -g app -M app && mkdir -p /app /data/storage && chown -R app:app /app /data
WORKDIR /app
COPY --from=build /build/backend/job-platform-api/target/job-platform-api-0.1.0-SNAPSHOT.jar /app/api.jar
COPY --from=build /build/backend/job-platform-mcp/target/job-platform-mcp-0.1.0-SNAPSHOT.jar /app/mcp.jar
COPY --from=build /build/backend/job-ingestion-worker/target/job-ingestion-worker-0.1.0-SNAPSHOT.jar /app/ingestion.jar
COPY --from=build /build/backend/ai-worker/target/ai-worker-0.1.0-SNAPSHOT.jar /app/ai.jar
COPY --from=build /build/backend/communication-worker/target/communication-worker-0.1.0-SNAPSHOT.jar /app/communication.jar
COPY --chmod=755 infra/docker/run-backend.sh /app/run.sh
ENV PORT=8080 RESUME_FONT_PATH=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf LOCAL_STORAGE_PATH=/data/storage
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=8 CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["/app/run.sh"]
