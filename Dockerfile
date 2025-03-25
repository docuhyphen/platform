# Use a base image with JDK 17 for Quarkus
FROM eclipse-temurin:21-jdk AS build

# Install LibreOffice
RUN apt-get update && apt-get install -y libreoffice && apt-get clean

# Set working directory
WORKDIR /app

# Copy Quarkus application files
COPY target/quarkus-app/ /app/

# Expose Quarkus application port
EXPOSE 8080

# Default command to run Quarkus
CMD ["java", "-jar", "/app/quarkus-run.jar"]
