# ── Stage 1: build Angular SPA ───────────────────────────────────────────────
# Uses the LTS Node version compatible with Angular 21.
FROM node:22-alpine AS frontend-build

WORKDIR /frontend

# Install dependencies before copying source for better layer caching.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts

# Copy source and build. Production config is the default (angular.json).
COPY frontend/ ./
RUN npm run build

# The production build lands at dist/locallink-frontend/browser/
# (outputPath set in angular.json).


# ── Stage 2: build Spring Boot jar ───────────────────────────────────────────
FROM eclipse-temurin:23-jdk-alpine AS backend-build

# /workspace mirrors the repo root layout.
# /workspace/backend  → pom.xml, src/, mvnw
# /workspace/seed     → pom.xml's ../seed reference resolves correctly
WORKDIR /workspace/backend

# Copy Maven wrapper and pom first for layer-cached dependency download.
COPY backend/.mvn           .mvn/
COPY backend/mvnw           backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy seed data one level up (pom.xml: <directory>../seed</directory>).
COPY seed /workspace/seed

# Copy backend source.
COPY backend/src src/

# Copy the Angular production assets into Spring Boot's static resource dir.
# Spring Boot serves everything under classpath:/static/ at the context root.
COPY --from=frontend-build \
     /frontend/dist/locallink-frontend/browser/ \
     src/main/resources/static/

# Package without tests (tests run in CI separately).
RUN ./mvnw package -DskipTests -q


# ── Stage 3: minimal runtime image ───────────────────────────────────────────
FROM eclipse-temurin:23-jre-alpine AS runtime

# Non-root user for least privilege.
RUN addgroup -S locallink && adduser -S locallink -G locallink
USER locallink

WORKDIR /app

COPY --from=backend-build /workspace/backend/target/locallink-backend-*.jar app.jar

# Spring Boot default port.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
