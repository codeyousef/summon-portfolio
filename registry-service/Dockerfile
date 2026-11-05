FROM eclipse-temurin:21-jre

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="Seen package registry" \
      org.opencontainers.image.description="Isolated Seen package registry service" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

RUN useradd --system \
        --uid 10001 \
        --user-group \
        --no-create-home \
        --home-dir /nonexistent \
        --shell /usr/sbin/nologin \
        seen-registry

WORKDIR /app

# The workflow builds and tests this standalone JAR before Docker is invoked.
# Keeping compilation outside this image makes the runtime JRE-only and avoids
# bringing the portfolio application or Seen compiler into the registry image.
COPY --chown=10001:10001 registry-service/build/libs/*-all.jar /app/seen-registry-service.jar
# Lets the same image run on Lambda. Ignored outside Lambda.
COPY --from=public.ecr.aws/awsguru/aws-lambda-adapter@sha256:46d6625e68cbbdd2efab4a20245977664513f13ffef47915b000d431adcea0b4 /lambda-adapter /opt/extensions/lambda-adapter

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    PORT=8080 \
    AWS_LWA_PORT=8080 \
    AWS_LWA_READINESS_CHECK_PATH=/health

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/seen-registry-service.jar"]
