# Copy React build into Quarkus static resources
# Quarkus serves these automatically at /
#COPY --from=react-build /web/dist /app/quarkus-app/app/META-INF/resources
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
#RUN npx tsc --noEmit || echo "Ignoring TS errors for Docker build"
# =========================
# Stage 2 – Quarkus runtime
# =========================
FROM eclipse-temurin:21-jdk

# Install LibreOffice
# --no-install-recommends keeps the layer as small as possible.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl libreoffice \
 && rm -rf /var/lib/apt/lists/*

# ── Non-root user ────────────────────────────────────────────────────────────
RUN groupadd -r appgroup --gid 1001 \
 && useradd -r -g appgroup --uid 1001 --create-home appuser

# Copy Quarkus application (already built by Maven)
COPY --chown=appuser:appgroup target/quarkus-app/ /app/
WORKDIR /app
# -XX:+ExitOnOutOfMemoryError : crash fast on OOM instead of limping along
# -Djava.util.logging.manager : required by JBoss LogManager (Quarkus)
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -XX:+UseG1GC \
  -XX:MaxRAMPercentage=70.0 \
  -XX:InitialRAMPercentage=25.0 \
  -XX:+ExitOnOutOfMemoryError \
  -Dfile.encoding=UTF-8"

EXPOSE 8080
# ── Health check ─────────────────────────────────────────────────────────────
# ALB also checks the Quarkus health endpoint, but this catches container-level issues
# before the ALB can even route traffic.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -f http://localhost:8080/q/health || exit 1

# ── Entrypoint ───────────────────────────────────────────────────────────────
# exec form ensures PID 1 receives SIGTERM cleanly (graceful ECS task shutdown).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]

USER appuser
