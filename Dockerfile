# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Maven wrapper and pom.xml first for better caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the JAR from build stage
COPY --from=build /app/target/league-ranker-0.0.1-SNAPSHOT.jar app.jar

# Expose the port
EXPOSE 8888

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
