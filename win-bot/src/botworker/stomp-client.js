'use strict';

const WebSocket = require('ws');

const NULL_BYTE = '\x00';

function buildFrame(command, headers = {}, body = '') {
  let frame = command + '\n';
  for (const [k, v] of Object.entries(headers)) frame += `${k}:${v}\n`;
  frame += '\n' + body + NULL_BYTE;
  return frame;
}

function parseFrame(raw) {
  const nullIdx = raw.indexOf('\x00');
  const text = nullIdx >= 0 ? raw.slice(0, nullIdx) : raw;
  // Normalize line endings: strip \r so \r\n works the same as \n
  const lines = text.split('\n').map(l => l.replace(/\r$/, ''));
  if (!lines.length || !lines[0].trim()) return null;

  const command = lines[0].trim();
  const headers = {};
  let i = 1;
  while (i < lines.length && lines[i].trim() !== '') {
    const colon = lines[i].indexOf(':');
    if (colon > 0) headers[lines[i].slice(0, colon).trim()] = lines[i].slice(colon + 1).trim();
    i++;
  }
  const body = lines.slice(i + 1).join('\n');
  return { command, headers, body };
}

class StompClient {
  constructor(serverUrl, authClient) {
    this.serverUrl = serverUrl;
    this.authClient = authClient;
    this.ws = null;
    this.connected = false;
    this.subscriptions = new Map(); // destination -> callback
    this.onConnectedCallbacks = [];
    this.onAssignCallback = null;
    this.accessToken = null;
    this.refreshToken = null;
    this._stopped = false;
    this._subCounter = 0;
    this._subIds = new Map(); // destination -> subId
  }

  onConnected(cb)  { this.onConnectedCallbacks.push(cb); }
  onAssign(cb)     { this.onAssignCallback = cb; }

  start() { this._loop(); }

  async _loop() {
    let attempt = 0;
    while (!this._stopped) {
      try {
        const tokens = await this.authClient.loginOrRegister();
        this.accessToken  = tokens.accessToken;
        this.refreshToken = tokens.refreshToken;
        attempt = 0;
        await this._connect();
      } catch (e) {
        console.error('[STOMP] Connection error:', e.message);
      }
      if (this._stopped) break;
      const delay = Math.min(Math.pow(2, attempt) * 1000, 60_000);
      console.log(`[STOMP] Reconnecting in ${delay / 1000}s...`);
      attempt++;
      await new Promise(r => setTimeout(r, delay));
    }
  }

  _connect() {
    return new Promise((resolve) => {
      const ws = new WebSocket(this.serverUrl);
      this.ws = ws;

      ws.on('open', () => {
        ws.send(buildFrame('CONNECT', {
          'accept-version': '1.1,1.2',
          'heart-beat': '10000,10000',
          'Authorization': `Bearer ${this.accessToken}`,
        }));
      });

      ws.on('message', (data) => {
        const raw = data.toString();
        if (raw === '\n') { ws.send('\n'); return; } // heartbeat pong
        const frame = parseFrame(raw);
        if (!frame) return;
        this._handleFrame(frame);
      });

      ws.on('close', (code, reason) => {
        console.warn(`[STOMP] Disconnected: ${code} ${reason}`);
        this.connected = false;
        resolve();
      });

      ws.on('error', (err) => {
        console.error('[STOMP] WS error:', err.message);
        this.connected = false;
        resolve();
      });
    });
  }

  _handleFrame(frame) {
    const { command, headers, body } = frame;

    if (command === 'CONNECTED') {
      this.connected = true;
      console.log('[STOMP] CONNECTED');

      // Subscribe to bot invite queue
      this._sendSubscribe('/user/queue/bot-invite', 'sub-bot-invite');

      // Re-subscribe all existing subscriptions
      for (const [dest, _] of this.subscriptions) {
        const sid = this._subIds.get(dest) || `sub-${++this._subCounter}`;
        this._subIds.set(dest, sid);
        this._sendSubscribe(dest, sid);
      }

      for (const cb of this.onConnectedCallbacks) {
        try { cb(); } catch (e) { console.error('[STOMP] onConnected error:', e); }
      }
      return;
    }

    if (command === 'MESSAGE') {
      const dest = headers.destination || '';
      let payload = {};
      if (body) { try { payload = JSON.parse(body); } catch {} }

      if (dest === '/user/queue/bot-invite' || dest.endsWith('/queue/bot-invite')) {
        if (this.onAssignCallback) this.onAssignCallback(payload);
        return;
      }

      for (const [topic, cb] of this.subscriptions) {
        if (dest === topic || dest.endsWith(topic)) { cb(payload); return; }
      }
      return;
    }

    if (command === 'ERROR') {
      console.error('[STOMP] ERROR frame:', headers.message, body);
    }
  }

  _sendSubscribe(dest, id) {
    if (this.ws && this.connected) {
      this.ws.send(buildFrame('SUBSCRIBE', { id, destination: dest }));
    }
  }

  subscribe(dest, cb) {
    if (!this._subIds.has(dest)) {
      const id = `sub-${++this._subCounter}`;
      this._subIds.set(dest, id);
      if (this.connected) this._sendSubscribe(dest, id);
    }
    this.subscriptions.set(dest, cb);
  }

  send(dest, payload) {
    if (!this.ws || !this.connected) {
      console.warn(`[STOMP] Cannot send to ${dest}: not connected`);
      return;
    }
    const body = JSON.stringify(payload);
    this.ws.send(buildFrame('SEND', {
      destination: dest,
      'content-type': 'application/json',
    }, body));
  }

  isConnected() { return this.connected; }
}

module.exports = { StompClient };
