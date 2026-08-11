'use strict';

const { BotAuthClient }  = require('./src/botworker/auth');
const { StompClient }    = require('./src/botworker/stomp-client');
const { SessionManager } = require('./src/botworker/session');
const { SearchEngine }   = require('./src/engine/search');

const SERVER_URL    = process.env.SERVER_URL    || 'ws://localhost:8080/ws';
const BACKEND_URL   = process.env.BACKEND_HTTP_URL || 'http://localhost:8080';
const BOT_USERNAME  = process.env.BOT_USERNAME  || 'win-bot';
const BOT_PASSWORD  = process.env.BOT_PASSWORD  || 'WinBot@password1';
const MANUAL_ROOM   = process.env.ROOM_ID;
const MANUAL_SIDE   = process.env.SIDE          || 'PLAYER_2';
const MANUAL_DIFF   = process.env.DIFFICULTY    || 'HARD';

console.log('=== WIN-BOT — Jungle Chess (JS) ===');
console.log(`Server:   ${SERVER_URL}`);
console.log(`Backend:  ${BACKEND_URL}`);
console.log(`Username: ${BOT_USERNAME}`);

const auth    = new BotAuthClient(BACKEND_URL, BOT_USERNAME, BOT_PASSWORD);
const engine  = new SearchEngine();
const stomp   = new StompClient(SERVER_URL, auth);
const manager = new SessionManager(stomp, engine);

stomp.start();

if (MANUAL_ROOM) {
  console.log(`Manual join: room=${MANUAL_ROOM} side=${MANUAL_SIDE} diff=${MANUAL_DIFF}`);
  // Give STOMP a moment to connect before assigning
  setTimeout(() => manager.assign(MANUAL_ROOM, MANUAL_SIDE, MANUAL_DIFF), 3000);
}

process.on('SIGINT',  () => { console.log('Shutting down...'); process.exit(0); });
process.on('SIGTERM', () => { console.log('Shutting down...'); process.exit(0); });
