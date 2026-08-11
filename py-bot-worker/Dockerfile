FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY src/ ./src/
COPY main.py ./

ENV SERVER_URL=ws://jc-be:8080/ws \
    BACKEND_HTTP_URL=http://jc-be:8080 \
    BOT_USERNAME=py-bot-worker \
    BOT_PASSWORD=PyBot@worker1 \
    DIFFICULTY=MEDIUM

CMD ["python", "main.py"]
