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

# Copy application JAR
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

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

# Mark /app/storage as a volume mount point
VOLUME ["/app/storage"]

EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
