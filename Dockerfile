# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S opsvision && adduser -S opsvision -G opsvision
USER opsvision

COPY --from=build /workspace/target/opsvision-backend-*.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS="" \
    SERVER_PORT=8080 \
    DB_URL=jdbc:postgresql://postgres:5432/opsvision \
    DB_USERNAME=opsvision \
    DB_PASSWORD=opsvision

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
