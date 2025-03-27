# Use a base image with JDK 21 for Quarkus
FROM eclipse-temurin:21-jdk AS build

# Install LibreOffice
RUN apt-get update && apt-get install -y libreoffice && apt-get clean

# Set working directory
WORKDIR /app

# Copy Quarkus application files
COPY target/quarkus-app/ /app/

# Expose Quarkus application port and debug port
EXPOSE 8080
EXPOSE 5005

# Default command to run Quarkus with debug options
CMD ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "/app/quarkus-run.jar"]