'use strict';

const https = require('https');
const http  = require('http');
const { URL } = require('url');

function httpPost(url, body) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const lib = parsed.protocol === 'https:' ? https : http;
    const data = JSON.stringify(body);
    const req = lib.request({
      hostname: parsed.hostname,
      port: parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
      path: parsed.pathname + (parsed.search || ''),
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data),
      },
    }, (res) => {
      let raw = '';
      res.on('data', d => raw += d);
      res.on('end', () => resolve({ status: res.statusCode, body: raw }));
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

class BotAuthClient {
  constructor(backendUrl, username, password) {
    this.backendUrl = backendUrl.replace(/\/$/, '');
    this.username = username;
    this.password = password;
  }

  async loginOrRegister() {
    try {
      return await this.login();
    } catch {
      console.log('[Auth] Login failed, attempting registration...');
      await this._register();
      return await this.login();
    }
  }

  async _register() {
    const res = await httpPost(`${this.backendUrl}/api/auth/bot-register`, {
      username: this.username,
      password: this.password,
      fullName: 'Win Bot (JS)',
    });
    if (res.status === 409) {
      throw new Error(
        `Username '${this.username}' is taken. Set a unique BOT_USERNAME and restart.`
      );
    }
    if (res.status !== 200 && res.status !== 201) {
      throw new Error(`Registration failed: ${res.status} ${res.body}`);
    }
  }

  async login() {
    const res = await httpPost(`${this.backendUrl}/api/auth/login`, {
      username: this.username,
      password: this.password,
    });
    if (res.status !== 200) throw new Error(`Login failed: ${res.status} ${res.body}`);
    const json = JSON.parse(res.body);
    const { accessToken, refreshToken } = json.data;
    if (!accessToken || !refreshToken) throw new Error('Missing tokens in login response');
    return { accessToken, refreshToken };
  }

  async refresh(refreshToken) {
    const res = await httpPost(`${this.backendUrl}/api/auth/refresh`, { refreshToken });
    if (res.status !== 200) throw new Error(`Token refresh failed: ${res.status}`);
    const json = JSON.parse(res.body);
    const { accessToken, refreshToken: newRefresh } = json.data;
    return { accessToken, refreshToken: newRefresh };
  }
}

module.exports = { BotAuthClient };
