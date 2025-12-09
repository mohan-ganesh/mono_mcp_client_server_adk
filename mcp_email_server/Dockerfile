
FROM maven:3.8.3-openjdk-17 as builder

# Copy local code to the container image.
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build a release artifact.
RUN mvn clean package -DskipTests


FROM eclipse-temurin:17-jre-alpine

# Copy the jar to the production image from the builder stage.
COPY --from=builder /app/target/mcp-email-server*.jar /mcp-email-server.jar

# Run the web service on container startup.
CMD ["java", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=dev", "-jar", "/mcp-email-server.jar"]