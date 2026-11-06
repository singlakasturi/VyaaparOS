FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/Backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER app:app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 CMD wget -q -O - http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
