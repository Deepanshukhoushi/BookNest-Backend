# syntax=docker/dockerfile:1
# Stage 1: Build the application
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app

# Copy the parent POM and configuration files
COPY pom.xml .
COPY lombok.config .

# Copy the service specific files
COPY wishlist-service/pom.xml wishlist-service/
WORKDIR /app/wishlist-service
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -Dhttp.keepAlive=false -Dmaven.wagon.http.pool=false -Dmaven.wagon.http.retryHandler.count=3

COPY wishlist-service/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -Dhttp.keepAlive=false -Dmaven.wagon.http.pool=false

# Stage 2: Run the application
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY --from=build /app/wishlist-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
