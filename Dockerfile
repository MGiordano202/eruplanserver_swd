# syntax=docker/dockerfile:1.7

############################
# Stage 1 - build
############################
# Java bytecode is platform independent: the build stage always runs natively
# on the builder's platform, even when producing the arm64 image (no slow emulation)
FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the pom first so the dependency layer is reused when only the sources change
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src
# Tests are executed by the CI pipeline (ci.yml), not during the image build
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -DskipTests \
 && cp target/*.jar app.jar \
 && java -Djarmode=layertools -jar app.jar extract --destination extracted

############################
# Stage 2 - runtime
############################
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="eruplanserver" \
      org.opencontainers.image.description="Eruplan backend (Spring Boot)" \
      org.opencontainers.image.source="https://github.com/T-R-M-V-spin-off/eruplanserver" \
      org.opencontainers.image.licenses="MIT"

# Unprivileged user: the application never runs as root
RUN addgroup -S spring && adduser -S -G spring -H spring
WORKDIR /app

# Spring Boot layers, ordered from least to most frequently changing
COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring:spring

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=local \
    SPRING_JPA_SHOW_SQL=false

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Exec form: the JVM is PID 1 and receives SIGTERM for a graceful shutdown
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
