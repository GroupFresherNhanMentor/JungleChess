# Build stage
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml ./
RUN mvn dependency:go-offline || true
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/target/jc-bot-worker-*.jar app.jar

ENV SERVER_URL=ws://jc-be:8080/ws \
    BACKEND_HTTP_URL=http://jc-be:8080

ENTRYPOINT ["java", "-jar", "app.jar"]
