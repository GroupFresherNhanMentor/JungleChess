'use strict';

const os = require('os');
const path = require('path');
const { Worker } = require('worker_threads');
const { Board, SIDE } = require('../engine/model');
const { SearchEngine, SearchContext } = require('../engine/search');

const MIN_MOVE_MS = 1200;

const DEPTH_BY_DIFFICULTY = {
  EASY:   3,
  MEDIUM: 6,
  HARD:   12,   // increased from 10
};

// Lazy SMP: number of helper worker threads
const NUM_HELPERS = Math.max(0, Math.min(os.cpus().length - 1, 3));
const WORKER_PATH = path.join(__dirname, '../engine/search-worker.js');

class GameSession {
  constructor(roomId, side, difficulty, engine, stomp) {
    this.roomId     = roomId;
    this.side       = side === 'PLAYER_1' ? SIDE.PLAYER_1 : SIDE.PLAYER_2;
    this.sideName   = side;
    this.difficulty = (difficulty || 'MEDIUM').toUpperCase();
    this.maxDepth   = DEPTH_BY_DIFFICULTY[this.difficulty] || 6;
    this.timeoutMs  = this.difficulty === 'HARD' ? 5000 : this.difficulty === 'MEDIUM' ? 2500 : 1200;
    this.engine     = engine;
    this.stomp      = stomp;
    this.ctx        = new SearchContext();
    this.ended      = false;
    this.computing  = false;
    this.moveNumber = 0;
  }

  handleEvent(payload) {
    if (this.ended) return;
    const type = payload.type;

    if (type === 'GAME_RESULT' || payload.winner !== undefined) {
      this.ended = true;
      console.log(`[Room ${this.roomId}] Game over. Winner: ${payload.winner}`);
      return;
    }

    if (type === 'STATE_UPDATED' || payload.board) {
      this._handleState(payload);
    }
  }

  _handleState(payload) {
    const status = payload.status;
    if (status === 'ENDED') { this.ended = true; return; }
    if (status !== 'PLAYING') return;
    if (this.computing) return;

    const currentTurn = payload.currentTurn;
    if (!currentTurn) return;

    const boardArr = payload.board;
    if (!boardArr) return;

    const board = Board.fromArray(boardArr);
    const turnSide = currentTurn === 'PLAYER_1' ? SIDE.PLAYER_1 : SIDE.PLAYER_2;
    this.moveNumber = payload.moveNumber || this.moveNumber;

    // Record position for repetition detection
    const posKey = board.posKey(turnSide);
    this.ctx.positionHistory.set(posKey, (this.ctx.positionHistory.get(posKey) || 0) + 1);

    if (turnSide !== this.side) return;

    this.computing = true;
    setImmediate(() => this._computeAndSend(board));
  }

  _computeAndSend(board) {
    const startMs = Date.now();

    // Lazy SMP: spawn helper workers that search the same position concurrently.
    // All threads share the TT via the module-level SharedArrayBuffer.
    const workers = [];
    for (let i = 0; i < NUM_HELPERS; i++) {
      const helperDepth = this.maxDepth + (i % 2 === 0 ? 1 : -1); // stagger depths
      const w = new Worker(WORKER_PATH, {
        workerData: {
          cells:    board.cells.buffer.slice(0),
          p1Pieces: board.p1Pieces.buffer.slice(0),
          p2Pieces: board.p2Pieces.buffer.slice(0),
          hashLo:   board.hashLo,
          hashHi:   board.hashHi,
          side:     this.side,
          depth:    Math.max(1, helperDepth),
          timeoutMs: this.timeoutMs,
        },
      });
      w.on('error', e => console.warn(`[Worker ${i}] error:`, e.message));
      workers.push(w);
    }

    try {
      const bestMove = this.engine.nextMove(board, this.side, this.maxDepth, this.timeoutMs, this.ctx);

      // Terminate helpers after main thread finishes
      for (const w of workers) w.terminate();

      if (!bestMove) {
        console.warn(`[Room ${this.roomId}] No move found!`);
        this.computing = false;
        return;
      }

      const elapsed = Date.now() - startMs;
      const wait = Math.max(0, MIN_MOVE_MS - elapsed);

      setTimeout(() => {
        const fromR = (bestMove.from / 7) | 0;
        const fromC = bestMove.from % 7;
        const toR   = (bestMove.to / 7) | 0;
        const toC   = bestMove.to % 7;

        this.stomp.send(`/app/room/${this.roomId}/move`, {
          from: [fromR, fromC],
          to:   [toR, toC],
        });
        console.log(`[Room ${this.roomId}] Move: (${fromR},${fromC}) -> (${toR},${toC}) [${Date.now() - startMs}ms, nodes:${this.engine.nodes}, helpers:${NUM_HELPERS}]`);
        this.computing = false;
      }, wait);
    } catch (e) {
      for (const w of workers) w.terminate();
      console.error(`[Room ${this.roomId}] Error computing move:`, e);
      this.computing = false;
    }
  }
}

class SessionManager {
  constructor(stomp, engine) {
    this.stomp    = stomp;
    this.engine   = engine;
    this.sessions = new Map(); // `${roomId}:${side}` -> GameSession
    this.topicSessions = new Map(); // topic -> GameSession[]
    this.pending  = [];

    stomp.onConnected(() => this._processPending());
    stomp.onAssign((data) => {
      const { roomId, side, difficulty } = data;
      if (roomId && side) this.assign(roomId, side, difficulty);
      else console.warn('[SessionManager] Malformed invite:', data);
    });
  }

  assign(roomId, side, difficulty) {
    const key = `${roomId}:${side}`;
    if (this.sessions.has(key)) { console.warn(`Already in room ${roomId} as ${side}`); return; }

    if (!this.stomp.isConnected()) {
      this.pending.push({ roomId, side, difficulty });
      return;
    }
    this._doAssign(roomId, side, difficulty);
  }

  _processPending() {
    // Re-send bot-join for existing sessions on reconnect
    for (const session of this.sessions.values()) {
      if (!session.ended) {
        this.stomp.send(`/app/room/${session.roomId}/bot-join`, {
          side: session.sideName, difficulty: session.difficulty,
        });
      }
    }
    for (const p of this.pending) this._doAssign(p.roomId, p.side, p.difficulty);
    this.pending = [];
  }

  _doAssign(roomId, side, difficulty) {
    const session = new GameSession(roomId, side, difficulty, this.engine, this.stomp);
    const key = `${roomId}:${side}`;
    this.sessions.set(key, session);

    const topic = `/topic/room/${roomId}`;
    if (!this.topicSessions.has(topic)) {
      this.topicSessions.set(topic, []);
      this.stomp.subscribe(topic, (payload) => {
        const sessions = this.topicSessions.get(topic) || [];
        for (const s of sessions) s.handleEvent(payload);
        // Cleanup ended sessions
        const alive = sessions.filter(s => !s.ended);
        if (alive.length !== sessions.length) {
          this.topicSessions.set(topic, alive);
          for (const s of sessions) {
            if (s.ended) this.sessions.delete(`${s.roomId}:${s.sideName}`);
          }
        }
      });
    }
    this.topicSessions.get(topic).push(session);

    this.stomp.send(`/app/room/${roomId}/bot-join`, { side, difficulty: difficulty || 'MEDIUM' });
    console.log(`[SessionManager] Assigned room=${roomId} side=${side} diff=${difficulty} depth=${session.maxDepth}`);
  }
}

module.exports = { SessionManager };
