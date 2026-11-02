# Stage 1: Build Frontend SPA
FROM node:24-alpine AS frontend-builder
WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./

ARG VITE_NEON_AUTH_URL
ARG VITE_SUPABASE_URL
ARG VITE_SUPABASE_PUBLISHABLE_KEY
ENV VITE_NEON_AUTH_URL=$VITE_NEON_AUTH_URL
ENV VITE_SUPABASE_URL=$VITE_SUPABASE_URL
ENV VITE_SUPABASE_PUBLISHABLE_KEY=$VITE_SUPABASE_PUBLISHABLE_KEY

RUN npm run build

# Stage 2: Build Spring Boot JAR
FROM maven:3.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /app/backend

COPY backend/pom.xml ./
RUN mvn dependency:go-offline -B || true

COPY backend/src ./src
COPY --from=frontend-builder /app/backend/src/main/resources/static ./src/main/resources/static

RUN mvn clean package -B -DskipTests=true

# Stage 3: Minimal Java 21 Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S wastelens && adduser -S wastelens -G wastelens
USER wastelens:wastelens

WORKDIR /app

COPY --from=backend-builder --chown=wastelens:wastelens /app/backend/target/wastelens-backend-*.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=60.0", \
  "-XX:+UseSerialGC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
