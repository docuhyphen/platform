# =========================
# Stage 1 – Build React app
# =========================
#FROM node:20-alpine AS react-build
#
#WORKDIR /web

## Copy package files first (better caching)
#COPY web-app/doc-hyphen/package*.json ./
#RUN npm install
#
## Copy the rest of the React app
#COPY web-app/doc-hyphen ./
## Disable TS type checks for Docker
#RUN npm install --save-dev @types/node
#RUN npx tsc --noEmit || echo "Ignoring TS errors for Docker build"
#RUN npm run build


# =========================
# Stage 2 – Quarkus runtime
# =========================
FROM eclipse-temurin:21-jdk

# Install LibreOffice
RUN apt-get update \
 && apt-get install -y libreoffice \
 && apt-get clean \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Quarkus application (already built by Maven)
COPY target/quarkus-app/ /app/

# Copy React build into Quarkus static resources
# Quarkus serves these automatically at /
#COPY --from=react-build /web/dist /app/quarkus-app/app/META-INF/resources

EXPOSE 8080
EXPOSE 5005

CMD ["java", \
  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", \
  "-jar", "/app/quarkus-run.jar"]
