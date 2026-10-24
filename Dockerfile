# build stage: JDK + Maven wrapper -> executable jar

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw && ./mvnw -B -ntp -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package

# runtime stage: JRE + a single jar

FROM eclipse-temurin:21-jre-alpine AS runtime

ARG OTEL_JAVAAGENT_VERSION=2.33.0

RUN apk add --no-cache curl && addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/*.jar app.jar

# Zero-code instrumentation. The agent is attached to every JVM and configured entirely
# through OTEL_* environment variables (see compose.apps.yaml), so the services keep a
# single, identical launch path.
ADD --chown=app:app https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

USER app
EXPOSE 8081
EXPOSE 9090
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -javaagent:/app/opentelemetry-javaagent.jar"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
