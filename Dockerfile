# Stage 1: Build Spring Boot with Java 21
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy complete Maven multi-module project
COPY . .

# Build all required modules
RUN mvn -B -ntp -DskipTests \
    -pl nourish-provider -am package

# Stage 2: Run Spring Boot
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build \
    /app/nourish-provider/target/nourish-provider.jar \
    /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:+UseSerialGC"

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
