FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src/main ./src/main
COPY src/test ./src/test
COPY storage/camera.properties ./storage/camera.properties
RUN mvn -B -ntp package
# Dependencias e classes do mesmo JAR que sera executado em producao.
RUN mkdir /build/ocr-check \
    && cd /build/ocr-check \
    && jar -xf /build/target/estacionatec-api-0.0.1-SNAPSHOT.jar BOOT-INF

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl ca-certificates \
        tesseract-ocr tesseract-ocr-eng libfontconfig1 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 estacionatec \
    && useradd --uid 10001 --gid estacionatec --no-create-home estacionatec \
    && mkdir -p /app /data/banco /data/imagens \
    && chown -R estacionatec:estacionatec /app /data
WORKDIR /app
COPY --from=build --chown=estacionatec:estacionatec /build/target/estacionatec-api-0.0.1-SNAPSHOT.jar /app/api.jar
COPY --chown=estacionatec:estacionatec storage/camera.properties /app/storage/camera.properties
USER estacionatec
ENV SPRING_PROFILES_ACTIVE=prod
# OcrClient usa o modelo instalado no Linux, sem extrair recursos do JAR.
ENV OCR_DATA_PATH=/usr/share/tesseract-ocr/5/tessdata
# Verifica dependencias nativas e leitura do modelo com o usuario da API.
RUN tesseract --version \
    && test -r "$OCR_DATA_PATH/eng.traineddata" \
    && tesseract --list-langs
# Valida tambem Tess4J/JNA e o processamento Java, nao apenas o executavel nativo.
# O mount temporario nao inclui classes nem imagens de teste na imagem final.
RUN --mount=type=bind,from=build,source=/build,target=/verification,ro \
    java -Djava.awt.headless=true \
    -cp "/verification/target/test-classes:/verification/ocr-check/BOOT-INF/classes:/verification/ocr-check/BOOT-INF/lib/*" \
    br.gov.sp.fatec.itu.estacionatec_api.OcrRuntimeCheck
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/api.jar"]
