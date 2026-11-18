# Build stage: compile and package the application with Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# Resolve dependencies first, so this layer stays cached while only the code changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Runtime stage: only a JRE and the jar, run by an unprivileged user
FROM eclipse-temurin:17-jre
RUN groupadd --system khronos && useradd --system --gid khronos --no-create-home khronos
WORKDIR /app
COPY --from=build /workspace/target/khronos-demo-*.jar app.jar
USER khronos
EXPOSE 8080
# Configuration comes from environment variables (see .env.example); JVM flags go in JAVA_TOOL_OPTIONS
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
