# Versoes nativas correspondentes a Tess4J 5.20.0 / Lept4J 1.24.0.
# Ubuntu Noble fornece Leptonica 1.82, sem simbolos exigidos pelo binding Java.
FROM ubuntu:24.04 AS ocr-native
RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential cmake pkg-config \
        curl ca-certificates libpng-dev libjpeg-dev libtiff-dev zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /native
RUN curl -fL --retry 3 https://codeload.github.com/DanBloomberg/leptonica/tar.gz/refs/tags/1.87.0 -o leptonica.tar.gz \
    && echo "fa2b40c5caea96d1bb93a97486262aed8731b69ce25a84a6bf5d25323e33f631  leptonica.tar.gz" | sha256sum -c - \
    && tar -xzf leptonica.tar.gz \
    && cmake -S leptonica-1.87.0 -B lept-build \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/opt/ocr -DCMAKE_INSTALL_LIBDIR=lib \
        -DBUILD_SHARED_LIBS=ON -DBUILD_PROG=OFF \
        -DENABLE_GIF=OFF -DENABLE_WEBP=OFF -DENABLE_OPENJPEG=OFF \
    && cmake --build lept-build --parallel 2 \
    && cmake --install lept-build
RUN curl -fL --retry 3 https://codeload.github.com/tesseract-ocr/tesseract/tar.gz/refs/tags/5.5.3 -o tesseract.tar.gz \
    && echo "9218e62793116d42a9f6d14cd9348518b27f382096eea3d0f2d1a24616bb5884  tesseract.tar.gz" | sha256sum -c - \
    && tar -xzf tesseract.tar.gz \
    && cmake -S tesseract-5.5.3 -B tess-build \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=/opt/ocr -DCMAKE_INSTALL_LIBDIR=lib \
        -DCMAKE_PREFIX_PATH=/opt/ocr -DBUILD_SHARED_LIBS=ON \
        -DBUILD_TRAINING_TOOLS=OFF -DBUILD_TESTS=OFF -DGRAPHICS_DISABLED=ON \
        -DOPENMP_BUILD=OFF -DDISABLE_ARCHIVE=ON -DDISABLE_CURL=ON \
    && cmake --build tess-build --parallel 2 \
    && cmake --install tess-build

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
        tesseract-ocr-eng libfontconfig1 libpng16-16t64 libjpeg-turbo8 libtiff6 zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 estacionatec \
    && useradd --uid 10001 --gid estacionatec --no-create-home estacionatec \
    && mkdir -p /app /data/banco /data/imagens \
    && chown -R estacionatec:estacionatec /app /data
COPY --from=ocr-native /opt/ocr /opt/ocr
RUN echo /opt/ocr/lib > /etc/ld.so.conf.d/estacionatec-ocr.conf \
    && ldconfig
ENV PATH="/opt/ocr/bin:${PATH}"
ENV LD_LIBRARY_PATH=/opt/ocr/lib
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata
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
