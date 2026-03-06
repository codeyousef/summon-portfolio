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

# Install LLVM 21 toolchain for Seen compiler (must match compiler's LLVM version)
RUN apt-get update && apt-get install -y --no-install-recommends wget gnupg ca-certificates \
    && wget -qO- https://apt.llvm.org/llvm-snapshot.gpg.key | gpg --dearmor -o /usr/share/keyrings/llvm.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/llvm.gpg] http://apt.llvm.org/noble/ llvm-toolchain-noble-21 main" \
       > /etc/apt/sources.list.d/llvm-21.list \
    && apt-get update && apt-get install -y --no-install-recommends \
       llvm-21 clang-21 lld-21 \
    && ln -sf /usr/bin/lli-21 /usr/bin/lli \
    && ln -sf /usr/bin/llvm-link-21 /usr/bin/llvm-link \
    && ln -sf /usr/bin/opt-21 /usr/bin/opt \
    && ln -sf /usr/bin/llc-21 /usr/bin/llc \
    && ln -sf /usr/bin/clang-21 /usr/bin/clang \
    && ln -sf /usr/bin/ld.lld-21 /usr/bin/ld.lld \
    && rm -rf /var/lib/apt/lists/*

# Copy Seen compiler and runtime.
# Before building this image, run: ./scripts/prepare-seen-tools.sh
# This populates seen-tools/ with the compiler binary and runtime.
COPY seen-tools/seen /opt/seen/seen
COPY seen-tools/seen_runtime/ /opt/seen/seen_runtime/
COPY seen-tools/languages/ /opt/seen/languages/
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
