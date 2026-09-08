FROM maven:3.9.11-eclipse-temurin-17@sha256:fa7aa19829157d299ff05f631b51697a388dcd2f6955e84249ecc652015f217b AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp test package

FROM debian:bookworm-slim@sha256:5ae3c39ebd15e229dcedd5cee596b2497182493d41ff162e824ba13fc1b2b867 AS runtime

ARG CHROMIUM_VERSION=152.0.7977.82-1~deb12u1
ARG CHROMIUM_DRIVER_VERSION=152.0.7977.82-1~deb12u1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        "chromium=${CHROMIUM_VERSION}" \
        "chromium-driver=${CHROMIUM_DRIVER_VERSION}" \
        fonts-noto-cjk \
        openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

RUN chromium_major="$(chromium --version | sed -E 's/[^0-9]*([0-9]+).*/\1/')" \
    && driver_major="$(chromedriver --version | sed -E 's/[^0-9]*([0-9]+).*/\1/')" \
    && test "$chromium_major" = "$driver_major" \
    && chromium --headless=new --no-sandbox --disable-dev-shm-usage --dump-dom about:blank > /tmp/headless-check.html \
    && grep -q '<html' /tmp/headless-check.html \
    && rm /tmp/headless-check.html

RUN useradd --system --uid 10001 --create-home appuser

WORKDIR /app
COPY --from=build /workspace/target/book-catalog-api-*.jar /app/app.jar

USER appuser
EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
