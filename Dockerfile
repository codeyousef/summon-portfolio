# Build stage: JDK only; run the project's Gradle Wrapper
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy wrapper first for better caching, then the rest
COPY gradlew /workspace/gradlew
COPY gradle /workspace/gradle
RUN chmod +x /workspace/gradlew

# (Optional) show wrapper version for debugging
RUN /workspace/gradlew --version || true

# Now copy everything else and build the fat JAR
COPY . /workspace

# Build the server (hydration assets now embedded in Summon 0.4.8.9+)
RUN /workspace/gradlew -x test shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install LLVM toolchain for Seen compiler (opt, llc, clang, lld)
RUN apt-get update && apt-get install -y --no-install-recommends \
    clang lld llvm \
    && rm -rf /var/lib/apt/lists/*

# Copy Seen compiler and runtime.
# Before building this image, run: ./scripts/prepare-seen-tools.sh
# This populates seen-tools/ with the compiler binary and runtime.
COPY seen-tools/seen /opt/seen/seen
COPY seen-tools/seen_runtime/ /opt/seen/seen_runtime/
RUN chmod +x /opt/seen/seen

# Copy application JAR (use the shadow/all JAR specifically)
COPY --from=build /workspace/build/libs/*-all.jar /app/app.jar

# Copy classpath resources (including static assets) alongside the JAR
COPY --from=build /workspace/src/main/resources/ /app/resources/

# Create storage directory with proper permissions
# NOTE: For data persistence, mount a volume at /app/storage
# e.g., docker run -v portfolio-data:/app/storage ...
RUN mkdir -p /app/storage && chmod 755 /app/storage

# Environment variables for storage paths
# These can be overridden at runtime with -e flags or in docker-compose
ENV PORT=8080
ENV PORTFOLIO_CONTENT_PATH=/app/storage/content.json
ENV ADMIN_CREDENTIALS_PATH=/app/storage/admin-credentials.json
ENV SEEN_HOME=/opt/seen
ENV SEEN_BINARY_PATH=/opt/seen/seen

# Mark /app/storage as a volume mount point
VOLUME ["/app/storage"]

EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
