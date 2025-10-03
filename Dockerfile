# Use OpenJDK 11 as base image
FROM openjdk:11-jre-slim

# Set working directory
WORKDIR /app

# Copy source code
COPY src/ src/
COPY pom.xml .

# Install Maven and build the application
RUN apt-get update && \
    apt-get install -y maven && \
    mkdir -p target/classes && \
    javac --release 11 -cp "src/main/java" -d target/classes src/main/java/org/example/server/WebSocketWhiteboardServerSimple.java && \
    apt-get remove -y maven && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

# Expose port
EXPOSE 8080

# Set environment variable for port
ENV PORT=8080

# Run the application
CMD ["java", "-cp", "target/classes", "org.example.server.WebSocketWhiteboardServerSimple"]