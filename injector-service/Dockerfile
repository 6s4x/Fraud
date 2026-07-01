FROM gradle:8-jdk17 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/fraudoor-injector-service-1.0.0-all.jar app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
