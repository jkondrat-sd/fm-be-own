# ============================================================
# FM-BE — Multi-stage Dockerfile
# ============================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

# Download dependencies first (layer caching)
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre-jammy

RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*
RUN groupadd -r fmapp && useradd -r -g fmapp fmapp

WORKDIR /app

COPY --from=builder /app/target/fm-be-*.jar app.jar

RUN chown -R fmapp:fmapp /app
USER fmapp

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/v1/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
