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

# Build the server (hydration assets now embedded in Summon 0.4.8.9+)
RUN /workspace/gradlew -x test shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy application JAR (shadow/fat JAR only)
COPY --from=build /workspace/build/libs/*-all.jar /app/app.jar

# Copy classpath resources (including static assets) alongside the JAR
COPY --from=build /workspace/src/main/resources/ /app/resources/

ENV PORT=8080
EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
