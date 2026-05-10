# syntax=docker/dockerfile:1

# Stage 1: Base builder - Resolve all dependencies
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy parent POM and config
COPY pom.xml .
COPY lombok.config .

# Copy all module POMs to allow dependency resolution
COPY admin-server/pom.xml admin-server/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY book-service/pom.xml book-service/
COPY cart-service/pom.xml cart-service/
COPY eureka-server/pom.xml eureka-server/
COPY notification-service/pom.xml notification-service/
COPY order-service/pom.xml order-service/
COPY review-service/pom.xml review-service/
COPY wallet-service/pom.xml wallet-service/
COPY wishlist-service/pom.xml wishlist-service/

# Resolve dependencies for all modules in one go
# Using batch mode and allowing connection pooling for better stability
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:resolve-plugins -B && \
    mvn dependency:go-offline -B || true

# --- Individual Service Build Stages ---

# Admin Server
FROM builder AS admin-server-build
COPY admin-server/src admin-server/src
RUN --mount=type=cache,target=/root/.m2 mvn -f admin-server/pom.xml clean package -DskipTests

# API Gateway
FROM builder AS api-gateway-build
COPY api-gateway/src api-gateway/src
RUN --mount=type=cache,target=/root/.m2 mvn -f api-gateway/pom.xml clean package -DskipTests

# Auth Service
FROM builder AS auth-service-build
COPY auth-service/src auth-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -f auth-service/pom.xml clean package -DskipTests

# Book Service
FROM builder AS book-service-build
COPY book-service/src book-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -f book-service/pom.xml clean package -DskipTests

# Cart Service
FROM builder AS cart-service-build
COPY cart-service/src cart-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -f cart-service/pom.xml clean package -DskipTests

# Eureka Server
FROM builder AS eureka-server-build
COPY eureka-server/src eureka-server/src
RUN --mount=type=cache,target=/root/.m2 mvn -f eureka-server/pom.xml clean package -DskipTests

# Notification Service
FROM builder AS notification-service-build
COPY notification-service/src notification-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -f notification-service/pom.xml clean package -DskipTests

# Order Service
FROM builder AS order-service-build
COPY order-service/src order-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -f order-service/pom.xml clean package -DskipTests

# Review Service
FROM builder AS review-service-build
COPY review-service/src review-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -f review-service/pom.xml clean package -DskipTests

# Wallet Service
FROM builder AS wallet-service-build
COPY wallet-service/src wallet-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -f wallet-service/pom.xml clean package -DskipTests

# Wishlist Service
FROM builder AS wishlist-service-build
COPY wishlist-service/src wishlist-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -f wishlist-service/pom.xml clean package -DskipTests

# --- Runtime Stages ---

FROM eclipse-temurin:17-jdk-alpine AS admin-server
WORKDIR /app
COPY --from=admin-server-build /app/admin-server/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS api-gateway
WORKDIR /app
COPY --from=api-gateway-build /app/api-gateway/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS auth-service
WORKDIR /app
COPY --from=auth-service-build /app/auth-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS book-service
WORKDIR /app
COPY --from=book-service-build /app/book-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS cart-service
WORKDIR /app
COPY --from=cart-service-build /app/cart-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS eureka-server
WORKDIR /app
COPY --from=eureka-server-build /app/eureka-server/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS notification-service
WORKDIR /app
COPY --from=notification-service-build /app/notification-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS order-service
WORKDIR /app
COPY --from=order-service-build /app/order-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS review-service
WORKDIR /app
COPY --from=review-service-build /app/review-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS wallet-service
WORKDIR /app
COPY --from=wallet-service-build /app/wallet-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-alpine AS wishlist-service
WORKDIR /app
COPY --from=wishlist-service-build /app/wishlist-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
