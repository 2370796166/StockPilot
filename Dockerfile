FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -Dmaven.repo.local=/root/.m2/repository -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/stockpilot-backend-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
