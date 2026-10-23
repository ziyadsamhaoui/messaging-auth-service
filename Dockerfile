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

RUN apk add --no-cache curl && addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/*.jar app.jar

USER app
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
