'use strict';
// Runs as a worker_thread — receives a board state and searches concurrently.
// Shares the TT with the main thread via the same SharedArrayBuffer.

const { workerData, parentPort } = require('worker_threads');
const { Board, SIDE } = require('./model');
const { SearchEngine, SearchContext } = require('./search');

const { cells, p1Pieces, p2Pieces, hashLo, hashHi, side, depth, timeoutMs } = workerData;

// Reconstruct board from transferred data
const board = new Board();
board.cells.set(new Uint8Array(cells));
board.p1Pieces.set(new Int8Array(p1Pieces));
board.p2Pieces.set(new Int8Array(p2Pieces));
board.hashLo = hashLo;
board.hashHi = hashHi;

const engine = new SearchEngine();
const ctx    = new SearchContext();

const move = engine.nextMove(board, side, depth, timeoutMs, ctx);
parentPort.postMessage(move ? { from: move.from, to: move.to, nodes: engine.nodes } : null);
