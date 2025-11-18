# Build stage: JDK only; run the project's Gradle Wrapper
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Copy wrapper first for better caching, then the rest
COPY gradlew /workspace/gradlew
COPY gradle /workspace/gradle
RUN chmod +x /workspace/gradlew

# (Optional) show wrapper version for debugging
RUN /workspace/gradlew --version || true

# Now copy everything else and build the fat JAR
COPY . /workspace

# Build the server and export Summon hydration JS assets into resources/static
RUN /workspace/gradlew -x test shadowJar :summonExportHydrationAssets --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy application JAR
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

# Copy classpath resources (including static assets) alongside the JAR
COPY --from=build /workspace/src/main/resources/ /app/resources/

ENV PORT=8080
EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
