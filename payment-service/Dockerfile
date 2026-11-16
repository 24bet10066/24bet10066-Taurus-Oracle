# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY shared-lib/pom.xml shared-lib/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY job-service/pom.xml job-service/pom.xml
COPY parts-service/pom.xml parts-service/pom.xml
COPY customer-service/pom.xml customer-service/pom.xml
COPY technician-service/pom.xml technician-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY analytics-service/pom.xml analytics-service/pom.xml
COPY shared-lib/src shared-lib/src
COPY payment-service/src payment-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl payment-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-0.0.1-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=production \
    JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=20 -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]
