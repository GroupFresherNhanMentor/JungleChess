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

ENV ROOM_ID=room-test \
    SERVER_URL=ws://jc-be:8080/ws \
    SIDE=PLAYER_2 \
    DIFFICULTY=MEDIUM

ENTRYPOINT ["java", "-jar", "app.jar"]
