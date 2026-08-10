FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/jc-bot-worker-*.jar app.jar

ENV ROOM_ID=room-test \
    SERVER_URL=ws://jc-be:8080/ws \
    SIDE=PLAYER_2 \
    DIFFICULTY=MEDIUM

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
