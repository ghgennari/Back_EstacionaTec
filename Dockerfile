FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/main ./src/main
COPY src/test ./src/test
RUN mvn -B -ntp package

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 estacionatec \
    && useradd --uid 10001 --gid estacionatec --no-create-home estacionatec \
    && mkdir -p /app /data/banco /data/imagens \
    && chown -R estacionatec:estacionatec /app /data
WORKDIR /app
COPY --from=build --chown=estacionatec:estacionatec /build/target/estacionatec-api-0.0.1-SNAPSHOT.jar /app/api.jar
USER estacionatec
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/api.jar"]
