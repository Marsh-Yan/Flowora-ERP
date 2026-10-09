FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services/api/pom.xml services/api/pom.xml
RUN mvn -B -pl services/api -am dependency:go-offline
COPY services/api services/api
RUN mvn -B -pl services/api -am -DskipTests package

# Pin the supported Ubuntu 24.04 variant instead of following the floating OS
# alias to Ubuntu 26.04, whose unused Pebble binary carries an older Go runtime.
# Java, CA/font libraries and readiness curl remain in the runtime image.
FROM eclipse-temurin:25.0.4.1_1-jre-noble
RUN apt-get update && apt-get upgrade -y --no-install-recommends \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 --create-home flowora
WORKDIR /app
COPY --from=build /workspace/services/api/target/flowora-api-2.0.0-SNAPSHOT.jar app.jar
RUN mkdir -p /var/lib/flowora/attachments /var/lib/flowora/exports && chown -R flowora:flowora /var/lib/flowora
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-Djava.security.egd=file:/dev/urandom","-jar","/app/app.jar","--spring.profiles.active=production"]
