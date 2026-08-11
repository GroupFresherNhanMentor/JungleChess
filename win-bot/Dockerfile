FROM node:20-slim
WORKDIR /app
COPY package.json ./
RUN npm install --production
COPY . .
ENV SERVER_URL=ws://jc-be:8080/ws \
    BACKEND_HTTP_URL=http://jc-be:8080 \
    BOT_USERNAME=win-bot \
    BOT_PASSWORD=WinBot@password1 \
    DIFFICULTY=HARD
CMD ["node", "main.js"]
