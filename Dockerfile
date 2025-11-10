# Multi-stage: build the fat JAR inside the container (CI-friendly)

FROM gradle:8.10.2-jdk17 AS build
WORKDIR /home/gradle/src

# Copy everything and build a shadow/fat JAR
COPY . .
# If you don't use the Shadow plugin, replace with your build command that produces a runnable jar in build/libs/
RUN gradle -x test shadowJar --no-daemon

# Runtime image
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=build /home/gradle/src/build/libs/*.jar /app/app.jar

# Cloud Run expects the app to bind to 0.0.0.0:$PORT
ENV PORT=8080
EXPOSE 8080

CMD ["java","-jar","/app/app.jar"]
