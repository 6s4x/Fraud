# Stage 1: Build the React panel
FROM node:20-alpine AS panel
WORKDIR /panel
COPY panel/package*.json ./
RUN npm ci && npm cache clean --force
COPY panel/ ./
RUN npm run build

# Stage 2: Build the Java injector
FROM gradle:8-jdk17 AS injector
WORKDIR /injector
COPY injector-service/build.gradle injector-service/settings.gradle ./
COPY injector-service/src ./src
RUN gradle shadowJar --no-daemon && rm -rf ~/.gradle

# Stage 3: Runtime - Node.js + Java 17
FROM node:20-alpine
RUN apk add --no-cache openjdk17-jre

WORKDIR /app
COPY server/package*.json ./
RUN npm ci --production && npm cache clean --force
COPY server/ ./

COPY --from=panel /panel/dist ./public
COPY --from=injector /injector/build/libs/fraudoor-injector-service-1.0.0.jar ./injector.jar

EXPOSE 8080
CMD ["node", "src/index.js"]
