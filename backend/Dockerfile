FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17.0.13_11-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system application \
    && useradd --system --gid application --home-dir /app application
WORKDIR /app
COPY --from=build --chown=application:application /app/target/*.jar /app/application.jar
USER application
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/api/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
