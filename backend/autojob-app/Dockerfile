FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw mvnw
COPY pom.xml pom.xml

COPY common common
COPY modules modules
COPY autojob-app autojob-app

RUN sed -i 's/\r$//' mvnw \
    && chmod +x mvnw \
    && ./mvnw \
        --batch-mode \
        --no-transfer-progress \
        -pl autojob-app \
        -am \
        -DskipTests \
        package

FROM eclipse-temurin:21-jre-jammy

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

WORKDIR /app

RUN apt-get update \
    && apt-get install \
        --yes \
        --no-install-recommends \
        curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd \
        --gid 10001 \
        autojob \
    && useradd \
        --uid 10001 \
        --gid autojob \
        --create-home \
        --home-dir /home/autojob \
        --shell /usr/sbin/nologin \
        autojob

COPY --from=build \
    /workspace/autojob-app/target/autojob-app-*.jar \
    /app/app.jar

RUN chown autojob:autojob /app/app.jar

USER autojob:autojob

EXPOSE 8080

STOPSIGNAL SIGTERM

HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=30s \
    --retries=5 \
    CMD curl \
        --fail \
        --silent \
        http://127.0.0.1:8080/actuator/health \
        > /dev/null \
        || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]