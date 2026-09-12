# ==========================================
# Stage 1: Build React/Vite Frontend
# ==========================================
FROM node:20-alpine AS frontend-build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@latest --activate
COPY package.json pnpm-lock.yaml ./
COPY patches/ ./patches/
RUN pnpm install --frozen-lockfile
COPY . .
# Build production bundle into dist/public (same-origin relative /api)
RUN pnpm run build

# ==========================================
# Stage 2: Build Spring Boot Backend with Static Assets
# ==========================================
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
# Copy compiled React frontend assets into Spring Boot static resources
COPY --from=frontend-build /app/dist/public/ ./src/main/resources/static/
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 3: Production Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
