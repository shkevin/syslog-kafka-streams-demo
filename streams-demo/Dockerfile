FROM openjdk:11-jdk-slim AS builder 

WORKDIR /app

# Copying only the project definition files first to fetch dependencies
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B dependency:go-offline

# RUN --mount=type=cache,target=/root/.m2 ./mvnw schema-registry:register -Dschema-registry-url=http://schema-registry:8081

# Now Java source code and build the project
# This ensures that the layer caching mechanism of Docker
# does not invalidate your entire build cache when source files change
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B package -DskipTests -Dcheckstyle.skip

# Copy the JAR file and log4j2.xml for both applications
FROM openjdk:11-jre-slim-buster AS base

# Define environment variables for Kafka and Schema Registry URLs
# These can be overridden by docker-compose or docker run -e
ENV BOOTSTRAP_SERVERS=localhost:9092 \
    SCHEMA_REGISTRY_URL=http://localhost:8081

# Copy the JAR file and log4j2.xml
COPY --from=builder /app/target/*-jar-with-dependencies.jar /app/app.jar
COPY --from=builder /app/src/main/resources/log4j2.xml /app/log4j2.xml

# Define the entrypoint for the producer application
FROM base AS producer

WORKDIR /app

ENTRYPOINT ["java", "-cp", "app.jar", "io.confluent.kafka.streams.SyslogProducer", "-Dlog4j.configurationFile=log4j2.xml"]

# Define the entrypoint for the runtime application
FROM base AS runtime

WORKDIR /app

# Install netcat and curl for health checks
RUN apt-get update && \
    apt-get install -y netcat curl && \
    rm -rf /var/lib/apt/lists/*

# Copy the entrypoint script and Avro schema files
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY scripts/entrypoint.sh /entrypoint.sh
COPY src/main/avro/*.avsc /app/src/main/avro/

RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]