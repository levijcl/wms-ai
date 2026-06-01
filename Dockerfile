# syntax=docker/dockerfile:1

# Multi-stage build: compile the Vue SPA, build the Spring Boot jar (with the SPA bundled into
# classpath:/static/), then ship a slim JRE runtime that serves everything single-origin on :8080.
# In-memory H2 means no external DB — the whole stack is this one image.

# --- Stage 1: build the Vue 3 + Vite console -------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
# Install deps first for layer caching, then build. Vite's outDir (frontend/vite.config.js) is
# ../src/main/resources/static, so the build lands at /app/src/main/resources/static.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Stage 2: build the Spring Boot boot jar -------------------------------------------------
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app
# Wrapper + build files first (caching), then sources.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
# Bundle the built SPA so Spring serves it from classpath:/static/.
COPY --from=frontend /app/src/main/resources/static ./src/main/resources/static
# Only the bootJar task runs, so no Spring Boot -plain.jar is produced; tests run via CI/local.
RUN ./gradlew bootJar -x test --no-daemon

# --- Stage 3: runtime ------------------------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /app/build/libs/ai-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
