# Stage 1: Build application using Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal runtime image optimized for Raspberry Pi (ARM64) and x86
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create data directory for SQLite database
RUN mkdir -p /data

# Copy built JAR from builder
COPY --from=builder /build/target/weekly-order-*.jar app.jar

# JVM flags optimized for low memory footprint on Raspberry Pi
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Xss512k"
ENV DB_PATH="/data/weeklyorder.db"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
