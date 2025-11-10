FROM gradle:8.10.2-jdk17 AS build
WORKDIR /home/gradle/src
COPY . .
RUN gradle -x test shadowJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
