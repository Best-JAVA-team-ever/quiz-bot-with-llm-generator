# Build Stage
FROM eclipse-temurin:25-jdk AS build
WORKDIR /home/gradle/src
COPY . .
RUN chmod +x gradlew && ./gradlew shadowJar --no-daemon -x test

# Run Stage
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /home/gradle/src/app/build/libs/app-1.0.0-RELEASE-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
