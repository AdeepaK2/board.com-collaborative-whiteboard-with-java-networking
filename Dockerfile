# Use OpenJDK 21 JDK (not JRE) for compilation
FROM openjdk:21-jdk-slim

# Set working directory
WORKDIR /app

# Copy source code
COPY src/ src/
COPY pom.xml .

# Build the application (no need for Maven since we're using javac directly)
RUN mkdir -p target/classes && \
    javac --release 21 -cp "src/main/java" -d target/classes src/main/java/org/example/server/MultiRoomWebSocketServer.java

# Expose port
EXPOSE 8080

# Set environment variable for port
ENV PORT=8080

# Run the application
CMD ["java", "-cp", "target/classes", "org.example.Main"]