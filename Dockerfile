# syntax=docker/dockerfile:1
#
# BadrLink — messaging-auth-service (credentials, login, JWT issuing)
#
# Build:  docker build -t ghcr.io/ziyadsamhaoui/messaging-auth-service:local .
# Run:    docker run --rm -p 8081:8081 --env-file .env <image>
#
# Configuration arrives as environment variables only: application.yml imports
# `.env` optionally, and nothing from a local .env is baked into the image.

# ---------- build stage: JDK + Maven wrapper -> executable jar ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependency resolution depends only on the POM, so it gets its own layer:
# editing Java sources then only rebuilds the copy/compile layers.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# A Windows checkout carries no Unix executable bit on mvnw.
RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

# ---------- runtime stage: JRE + a single jar, non-root ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl backs the container healthcheck; a JRE image has no HTTP client.
RUN apk add --no-cache curl && addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/*.jar app.jar

USER app
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
