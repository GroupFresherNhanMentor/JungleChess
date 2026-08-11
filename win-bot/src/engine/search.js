'use strict';

const { SIDE, OPP, P2_DEN_IDX, P1_DEN_IDX } = require('./model');
const { getMoves, checkWinner } = require('./rules');
const { evaluate, WIN_SCORE, LOSS_SCORE } = require('./evaluator');

const INF     = 10_000_000;
const MAX_PLY = 128;

// TT flags
const TT_EXACT = 0, TT_LOWER = 1, TT_UPPER = 2;

// TT: 4M entries via SharedArrayBuffer so workers share it
const TT_SIZE  = 1 << 22;
const TT_MASK  = TT_SIZE - 1;
// Layout per entry (12 bytes): key(4) score(4) depth(1) flag(1) from(1) to(1)
const TT_STRIDE = 12;
const sharedTTBuffer = new SharedArrayBuffer(TT_SIZE * TT_STRIDE);
const _ttView = new DataView(sharedTTBuffer);

function ttSlot(key32)       { return (key32 & TT_MASK) * TT_STRIDE; }
function ttGetKey(off)       { return _ttView.getInt32(off, true); }
function ttGetScore(off)     { return _ttView.getInt32(off + 4, true); }
function ttGetDepth(off)     { return _ttView.getInt8(off + 8); }
function ttGetFlag(off)      { return _ttView.getUint8(off + 9); }
function ttGetFrom(off)      { return _ttView.getInt8(off + 10); }
function ttGetTo(off)        { return _ttView.getInt8(off + 11); }
function ttSet(off, key32, score, depth, flag, from, to) {
  _ttView.setInt32(off,     key32, true);
  _ttView.setInt32(off + 4, score, true);
  _ttView.setInt8(off + 8,  depth);
  _ttView.setUint8(off + 9, flag);
  _ttView.setInt8(off + 10, from);
  _ttView.setInt8(off + 11, to);
}

function ttLookup(key32) {
  const off = ttSlot(key32);
  return ttGetKey(off) === key32 ? off : -1;
}

function ttStore(key32, depth, score, flag, from, to) {
  const off = ttSlot(key32);
  const existDepth = ttGetDepth(off);
  if (ttGetKey(off) !== key32 || depth >= existDepth) {
    ttSet(off, key32, score, depth, flag, from, to);
  }
}

// ---- Search Context (per-thread state, NOT shared) ----
class SearchContext {
  constructor() {
    this.positionHistory = new Map();
    this.killers  = Array.from({ length: MAX_PLY }, () => [null, null]);
    this.history  = [
      Array.from({ length: 63 }, () => new Int32Array(63)),
      Array.from({ length: 63 }, () => new Int32Array(63)),
    ];
    this.counterMove = new Map();
  }

  push(key) { this.positionHistory.set(key, (this.positionHistory.get(key) || 0) + 1); }
  pop(key)  {
    const n = this.positionHistory.get(key);
    if (n <= 1) this.positionHistory.delete(key);
    else this.positionHistory.set(key, n - 1);
  }
  repCount(key) { return this.positionHistory.get(key) || 0; }

  addKiller(ply, m) {
    const k = this.killers[ply];
    if (!k[0] || k[0].from !== m.from || k[0].to !== m.to) { k[1] = k[0]; k[0] = m; }
  }

  addHistory(m, depth) {
    const s = m.movedSide;
    this.history[s][m.from][m.to] += depth * depth;
    if (this.history[s][m.from][m.to] > 200_000) {
      for (let f = 0; f < 63; f++)
        for (let t = 0; t < 63; t++) {
          this.history[0][f][t] >>= 1;
          this.history[1][f][t] >>= 1;
        }
    }
  }

  getHistory(m) { return this.history[m.movedSide][m.from][m.to]; }
  setCounter(pf, pt, m) { this.counterMove.set(pf * 63 + pt, m); }
  getCounter(pf, pt)    { return this.counterMove.get(pf * 63 + pt) || null; }

  newSearch() {
    for (let p = 0; p < MAX_PLY; p++) { this.killers[p][0] = null; this.killers[p][1] = null; }
  }
}

// ---- Move ordering ----
function scoreMoveOrder(m, ttFrom, ttTo, killers, counter, ctx) {
  const { from, to, movedRank, capRank, movedSide } = m;
  if (from === ttFrom && to === ttTo) return 3_000_000;

  const enDen = movedSide === SIDE.PLAYER_1 ? P2_DEN_IDX : P1_DEN_IDX;
  if (to === enDen) return 2_900_000;

  if (capRank > 0) {
    if (movedRank === 1 && capRank === 8) return 2_800_000; // RAT kills ELEPHANT
    return 2_000_000 + capRank * 100 - movedRank;           // MVV-LVA
  }

  if (killers[0] && killers[0].from === from && killers[0].to === to) return 900_000;
  if (killers[1] && killers[1].from === from && killers[1].to === to) return 800_000;
  if (counter  && counter.from  === from && counter.to  === to)       return 700_000;

  // History + approach bonus: reward moves that close distance to enemy den
  const enDenR = movedSide === SIDE.PLAYER_1 ? 8 : 0;
  const fromR  = (from / 7) | 0;
  const toR    = (to / 7) | 0;
  const fromDist = Math.abs(fromR - enDenR) + Math.abs(from % 7 - 3);
  const toDist   = Math.abs(toR   - enDenR) + Math.abs(to % 7 - 3);
  return ctx.getHistory(m) + (fromDist - toDist) * 8;
}

function orderMoves(moves, ttFrom, ttTo, ply, ctx, prevMove) {
  const killers = ctx.killers[ply];
  const counter = prevMove ? ctx.getCounter(prevMove.from, prevMove.to) : null;
  moves.sort((a, b) =>
    scoreMoveOrder(b, ttFrom, ttTo, killers, counter, ctx) -
    scoreMoveOrder(a, ttFrom, ttTo, killers, counter, ctx)
  );
}

// ---- LMR table ----
const LMR = Array.from({ length: 64 }, (_, d) =>
  Array.from({ length: 64 }, (_, i) => {
    if (d < 3 || i < 3) return 0;
    return Math.max(1, Math.floor(0.75 + Math.log(d) * Math.log(i) / 2.25));
  })
);

// ---- SearchEngine ----
class SearchEngine {
  constructor() {
    this.nodes    = 0;
    this.deadline = 0;
  }

  nextMove(board, side, maxDepth, timeoutMs, ctx) {
    ctx.newSearch();
    this.nodes    = 0;
    this.deadline = Date.now() + timeoutMs;
    const softDeadline = Date.now() + timeoutMs * 0.45;

    const moves = getMoves(board, side);
    if (!moves.length) return null;
    if (moves.length === 1) return moves[0];

    // Forced win: check if any move immediately enters enemy den
    const enDen = side === SIDE.PLAYER_1 ? P2_DEN_IDX : P1_DEN_IDX;
    for (const m of moves) { if (m.to === enDen) return m; }

    let bestMove = moves[0];
    let bestScore = -INF;

    for (let depth = 1; depth <= maxDepth; depth++) {
      if (depth > 2 && Date.now() > softDeadline) break;

      // Aspiration window with multi-level widening
      let delta = 55;
      let alpha = depth > 2 && Math.abs(bestScore) < WIN_SCORE / 2 ? bestScore - delta : -INF;
      let beta  = depth > 2 && Math.abs(bestScore) < WIN_SCORE / 2 ? bestScore + delta : INF;

      let [move, score, aborted] = this._searchRoot(board, moves, side, depth, alpha, beta, ctx);

      // Widen window on fail, up to 3 times
      let tries = 0;
      while (!aborted && tries < 3 && (score <= alpha || score >= beta)) {
        delta *= 3;
        alpha = score <= alpha ? score - delta : -INF;
        beta  = score >= beta  ? score + delta :  INF;
        [move, score, aborted] = this._searchRoot(board, moves, side, depth, alpha, beta, ctx);
        tries++;
      }

      if (!aborted && move) { bestMove = move; bestScore = score; }
      if (aborted) break;
      if (Math.abs(bestScore) >= WIN_SCORE - MAX_PLY) break; // forced win found
    }

    return bestMove;
  }

  _searchRoot(board, moves, side, depth, alpha, beta, ctx) {
    const key32  = board.hashKey(side);
    const ttOff  = ttLookup(key32);
    const ttFrom = ttOff >= 0 ? ttGetFrom(ttOff) : -1;
    const ttTo   = ttOff >= 0 ? ttGetTo(ttOff)   : -1;
    orderMoves(moves, ttFrom, ttTo, 0, ctx, null);

    let bestMove = null, bestScore = -INF, aborted = false;

    for (let i = 0; i < moves.length; i++) {
      if (Date.now() > this.deadline) { aborted = true; break; }
      const m = moves[i];
      board.makeMove(m);
      this.nodes++;

      let score;
      if (i === 0) {
        score = -this._negamax(board, depth - 1, 1, -beta, -alpha, OPP[side], ctx, m);
      } else {
        score = -this._negamax(board, depth - 1, 1, -alpha - 1, -alpha, OPP[side], ctx, m);
        if (!aborted && score > alpha && score < beta)
          score = -this._negamax(board, depth - 1, 1, -beta, -alpha, OPP[side], ctx, m);
      }

      board.undoMove(m);
      if (Date.now() > this.deadline && i > 0) { aborted = true; break; }

      if (score > bestScore) { bestScore = score; bestMove = m; }
      if (score > alpha) alpha = score;
      if (alpha >= beta) break;
    }

    return [bestMove, bestScore, aborted];
  }

  _negamax(board, depth, ply, alpha, beta, side, ctx, prevMove) {
    // Time
    if (Date.now() > this.deadline) return evaluate(board, side);

    // Repetition draw
    const posKey = board.posKey(side);
    if (ctx.repCount(posKey) >= 2) return 0;

    // Terminal
    const winner = checkWinner(board);
    if (winner !== null) return winner === side ? WIN_SCORE - ply : LOSS_SCORE + ply;

    if (depth <= 0) return this._qsearch(board, alpha, beta, side, ply, ctx);

    const key32  = board.hashKey(side);
    let ttFrom = -1, ttTo = -1, ttScore = 0, ttFlag = TT_EXACT;
    const ttOff = ttLookup(key32);
    if (ttOff >= 0) {
      ttFrom = ttGetFrom(ttOff); ttTo = ttGetTo(ttOff);
      const td = ttGetDepth(ttOff);
      if (td >= depth) {
        ttScore = ttGetScore(ttOff); ttFlag = ttGetFlag(ttOff);
        if (ttFlag === TT_EXACT) return ttScore;
        if (ttFlag === TT_LOWER && ttScore > alpha) alpha = ttScore;
        if (ttFlag === TT_UPPER && ttScore < beta)  beta  = ttScore;
        if (alpha >= beta) return ttScore;
      }
    }

    const staticEval = evaluate(board, side);

    // ---- Razoring ----
    if (depth <= 2 && !this._isWinPly(board, side)) {
      const margin = depth === 1 ? 320 : 640;
      if (staticEval + margin <= alpha) {
        const qv = this._qsearch(board, alpha - margin, beta, side, ply, ctx);
        if (qv + margin <= alpha) return qv;
        if (depth === 1) return qv;
      }
    }

    // ---- Null Move Pruning ----
    const nmpOk = depth >= 3
      && beta < WIN_SCORE / 2 && alpha > LOSS_SCORE / 2
      && staticEval >= beta
      && this._pieceCount(board, side) >= 3;

    if (nmpOk) {
      const R = depth >= 6 ? 4 : depth >= 4 ? 3 : 2;
      ctx.push(posKey);
      const nmScore = -this._negamax(board, depth - R - 1, ply + 1, -beta, -beta + 1, OPP[side], ctx, null);
      ctx.pop(posKey);
      if (nmScore >= beta) return beta;
    }

    // ---- Internal Iterative Deepening (IID) ----
    if (ttFrom < 0 && depth >= 5) {
      this._negamax(board, depth - 3, ply, alpha, beta, side, ctx, prevMove);
      const iidOff = ttLookup(key32);
      if (iidOff >= 0) { ttFrom = ttGetFrom(iidOff); ttTo = ttGetTo(iidOff); }
    }

    const moves = getMoves(board, side);
    if (!moves.length) return LOSS_SCORE + ply;

    orderMoves(moves, ttFrom, ttTo, ply, ctx, prevMove);

    // ---- Singular Extension pre-check (before move loop, on current board) ----
    // Pass move list to avoid re-generating inside SE
    let singularFrom = -1, singularTo = -1;
    if (depth >= 6 && ttFrom >= 0) {
      if (this._singularExt(board, ttFrom, ttTo, depth, alpha, side, ctx, posKey, moves)) {
        singularFrom = ttFrom; singularTo = ttTo;
      }
    }

    const alphaOrig = alpha;
    let bestScore = -INF, bestMove = null;

    // Pull killers/counter before loop so LMR can check them
    const plyKillers = ctx.killers[ply];
    const plyCounter = prevMove ? ctx.getCounter(prevMove.from, prevMove.to) : null;

    ctx.push(posKey);

    for (let i = 0; i < moves.length; i++) {
      const m = moves[i];
      const isCapture = m.capRank > 0;
      const isWinMove = this._isWinMove(m, side);
      const isKiller  = (plyKillers[0]?.from === m.from && plyKillers[0]?.to === m.to) ||
                        (plyKillers[1]?.from === m.from && plyKillers[1]?.to === m.to);
      const isCounter = plyCounter?.from === m.from && plyCounter?.to === m.to;

      board.makeMove(m);
      this.nodes++;

      // ---- Extensions ----
      let ext = 0;
      if (isWinMove) ext = 1;
      else if (m.from === singularFrom && m.to === singularTo) ext = 1;
      else if (this._hasDenThreat(board, OPP[side])) ext = 1;

      let score;

      if (i === 0) {
        score = -this._negamax(board, depth - 1 + ext, ply + 1, -beta, -alpha, OPP[side], ctx, m);
      } else {
        // ---- Futility Pruning ----
        if (!isCapture && !isWinMove && depth <= 2 && !ext) {
          const margin = depth === 1 ? 350 : 700;
          if (staticEval + margin <= alpha) { board.undoMove(m); continue; }
        }

        // ---- LMR: never reduce killers, counter moves, or tactical moves ----
        const lmrDepth = (isCapture || isWinMove || ext > 0 || isKiller || isCounter)
          ? 0
          : LMR[Math.min(depth, 63)][Math.min(i, 63)];

        // Null window with LMR
        score = -this._negamax(board, depth - 1 - lmrDepth + ext, ply + 1, -alpha - 1, -alpha, OPP[side], ctx, m);

        if (score > alpha && lmrDepth > 0) {
          // Re-search full depth, still null window
          score = -this._negamax(board, depth - 1 + ext, ply + 1, -alpha - 1, -alpha, OPP[side], ctx, m);
        }
        if (score > alpha && score < beta) {
          // Full window re-search (PVS fail-high)
          score = -this._negamax(board, depth - 1 + ext, ply + 1, -beta, -alpha, OPP[side], ctx, m);
        }
      }

      board.undoMove(m);

      if (score > bestScore) { bestScore = score; bestMove = m; }
      if (score > alpha) {
        alpha = score;
        if (alpha >= beta) {
          if (!isCapture) {
            ctx.addKiller(ply, m);
            ctx.addHistory(m, depth);
            if (prevMove) ctx.setCounter(prevMove.from, prevMove.to, m);
          }
          break;
        }
      }
    }

    ctx.pop(posKey);

    const flag = bestScore <= alphaOrig ? TT_UPPER : bestScore >= beta ? TT_LOWER : TT_EXACT;
    ttStore(key32, depth, bestScore, flag, bestMove ? bestMove.from : -1, bestMove ? bestMove.to : -1);

    return bestScore;
  }

  _singularExt(board, ttFrom, ttTo, depth, alpha, side, ctx, posKey, moves) {
    // Called BEFORE the move loop on the current (unmodified) board.
    // Returns true if the TT move is singular (all alternatives fail below singularBeta).
    // Reuses the already-generated move list to avoid a redundant getMoves call.
    const singularBeta = alpha - depth * 2;
    if (singularBeta <= LOSS_SCORE) return false;

    ctx.push(posKey);
    for (const m of moves) {
      if (m.from === ttFrom && m.to === ttTo) continue;
      board.makeMove(m);
      this.nodes++;
      const sc = -this._negamax(board, (depth >> 1) - 1, 0, -singularBeta, -singularBeta + 1, OPP[side], ctx, m);
      board.undoMove(m);
      if (sc >= singularBeta) { ctx.pop(posKey); return false; }
    }
    ctx.pop(posKey);
    return true;
  }

  _qsearch(board, alpha, beta, side, ply, ctx) {
    const winner = checkWinner(board);
    if (winner !== null) return winner === side ? WIN_SCORE - ply : LOSS_SCORE + ply;

    const standPat = evaluate(board, side);
    if (standPat >= beta) return beta;
    if (standPat > alpha) alpha = standPat;

    const enDen = side === SIDE.PLAYER_1 ? P2_DEN_IDX : P1_DEN_IDX;
    const moves  = getMoves(board, side);
    const noisy  = moves.filter(m => m.capRank > 0 || m.to === enDen);
    if (!noisy.length) return standPat;
    orderMoves(noisy, -1, -1, ply, ctx, null);

    for (const m of noisy) {
      // Delta pruning: skip captures that can't help even with full gain
      if (m.capRank > 0 && standPat + MAT_APPROX[m.capRank] + 200 < alpha) continue;

      board.makeMove(m);
      this.nodes++;
      const score = -this._qsearch(board, -beta, -alpha, OPP[side], ply + 1, ctx);
      board.undoMove(m);

      if (score >= beta) return beta;
      if (score > alpha) alpha = score;
    }
    return alpha;
  }

  _isWinMove(m, side) {
    return m.to === (side === SIDE.PLAYER_1 ? P2_DEN_IDX : P1_DEN_IDX);
  }

  _isWinPly(board, side) {
    const enDen = side === SIDE.PLAYER_1 ? P2_DEN_IDX : P1_DEN_IDX;
    const moves = getMoves(board, side);
    return moves.some(m => m.to === enDen);
  }

  _hasDenThreat(board, side) {
    // Does 'side' have a piece adjacent to enemy den (can win in 1)?
    const enDen = side === SIDE.PLAYER_1 ? P2_DEN_IDX : P1_DEN_IDX;
    const { cells } = board;
    const neigh = side === SIDE.PLAYER_1
      ? [P2_DEN_IDX - 7, P2_DEN_IDX + 1, P2_DEN_IDX - 1]
      : [P1_DEN_IDX + 7, P1_DEN_IDX + 1, P1_DEN_IDX - 1];
    for (const ni of neigh) {
      if (ni < 0 || ni >= 63) continue;
      const c = cells[ni];
      if (c !== 0 && (c <= 8 ? 0 : 1) === side) return true;
    }
    return false;
  }

  _pieceCount(board, side) {
    const pieces = side === SIDE.PLAYER_1 ? board.p1Pieces : board.p2Pieces;
    let n = 0;
    for (let i = 0; i < 8; i++) if (pieces[i] >= 0) n++;
    return n;
  }
}

// Approximate material for delta pruning
const MAT_APPROX = [0, 180, 270, 370, 470, 570, 720, 780, 880];

module.exports = { SearchEngine, SearchContext, sharedTTBuffer };
