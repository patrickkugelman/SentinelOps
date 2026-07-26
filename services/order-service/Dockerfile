# syntax=docker/dockerfile:1
# ---- build (JDK 21) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 1001 spring
USER spring
COPY --from=build /app/target/order-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
