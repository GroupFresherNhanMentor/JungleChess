'use strict';

const {
  SIDE, OPP, ROWS, COLS, isRiver, isTrap,
  cellSide, cellRank, P1_DEN_IDX, P2_DEN_IDX,
} = require('./model');

const WIN_SCORE  =  1_000_000;
const LOSS_SCORE = -1_000_000;
const DRAW_SCORE = 0;

// Material — tuned for endgame importance
const MAT = [0, 180, 270, 370, 470, 570, 720, 780, 880]; // by rank 1..8

// ---- Piece-Square Tables (PLAYER_1 perspective, rows 0..8 toward enemy) ----
// Row 0 = own home row, Row 8 = enemy den row. Column 3 = center.

const PST_DEFAULT = [
  [  0,  4,  8, -60,  8,  4,   0],
  [  4,  8, 14,  22, 14,  8,   4],
  [  8, 14, 20,  28, 20, 14,   8],
  [ 12, 18, 14,  18, 14, 18,  12],
  [ 18, 24, 18,  24, 18, 24,  18],
  [ 24, 30, 24,  30, 24, 30,  24],
  [ 32, 40, 46,  56, 46, 40,  32],
  [ 42, 54, 66,  82, 66, 54,  42],
  [ 52, 68, 84, -999,84, 68,  52],
];

// Lion / Tiger: benefit most from being near river for jump threats
const PST_JUMPER = [
  [  0,  4,  8, -60,  8,  4,   0],
  [  4,  8, 14,  18, 14,  8,   4],
  [ 22, 34, 40,  34, 40, 34,  22],  // row 2: adjacent to river, best jump position
  [ 14, 20, 12,  14, 12, 20,  14],  // row 3: in river zone — can't be here
  [ 20, 26, 16,  20, 16, 26,  20],
  [ 26, 32, 20,  26, 20, 32,  26],
  [ 28, 44, 50,  68, 50, 44,  28],
  [ 48, 60, 76,  90, 76, 60,  48],
  [ 58, 74, 94, -999,94, 74,  58],
];

// Rat: river squares are golden; threaten elephant from river
const PST_RAT = [
  [  0,  4,  8, -60,  8,  4,   0],
  [  4,  8, 14,  18, 14,  8,   4],
  [ 14, 24, 28,  22, 28, 24,  14],
  [ 22, 44, 50,  32, 50, 44,  22],  // river row — blocks jumpers
  [ 28, 50, 56,  38, 56, 50,  28],  // river row — blocks jumpers
  [ 34, 50, 56,  44, 56, 50,  34],  // river row — blocks jumpers
  [ 38, 44, 50,  68, 50, 44,  38],
  [ 48, 60, 70,  86, 70, 60,  48],
  [ 58, 74, 90, -999,90, 74,  58],
];

// Elephant: strong attacker, must avoid own rat; prefer flanks to dodge enemy rat
const PST_ELEPHANT = [
  [  0,  4,  8, -60,  8,  4,   0],
  [  4,  8, 14,  18, 14,  8,   4],
  [  8, 14, 18,  22, 18, 14,   8],
  [ 10, 16, 10,  16, 10, 16,  10],
  [ 16, 20, 16,  20, 16, 20,  16],
  [ 20, 26, 20,  26, 20, 26,  20],
  [ 26, 34, 36,  44, 36, 34,  26],
  [ 36, 46, 56,  70, 56, 46,  36],
  [ 46, 60, 76, -999,76, 60,  46],
];

function getPST(rank, r, c, side) {
  const row = side === SIDE.PLAYER_1 ? r : (8 - r);
  let val;
  if      (rank === 1)            val = PST_RAT[row][c];
  else if (rank === 6 || rank === 7) val = PST_JUMPER[row][c];
  else if (rank === 8)            val = PST_ELEPHANT[row][c];
  else                            val = PST_DEFAULT[row][c];
  return val === -999 ? -9999 : val;
}

const P1_TRAP_LIST = [[0,2],[0,4],[1,3]];
const P2_TRAP_LIST = [[8,2],[8,4],[7,3]];

// Den-adjacent squares (pieces here threaten to enter den next turn)
const P1_DEN_ADJ = [3 - 7, 3 + 1, 3 - 1].filter(i => i >= 0); // (0,3) neighbours on the board
// P1 den at flat 3 = row0,col3. Up = -7 (OOB), right=4, left=2, down=10
const P1_DEN_ADJACENT = [4, 2, 10].filter(i => i >= 0 && i < 63); // right, left, down
const P2_DEN_ADJACENT = [58, 54, 56];   // P2 den at 59: left=58, right=60(OOB->use 58,54,56)
// P2 den = row8,col3=59: up=52, left=58, right=60(OOB). Correct: up=52, left=58
const P1_DEN_NEIGHBORS = [2, 4, 10];  // (0,2),(0,4),(1,3)
const P2_DEN_NEIGHBORS = [57, 61, 49]; // (8,2),(8,4),(7,3) = 8*7+2=58, 8*7+4=60, 7*7+3=52
// Recalculate properly:
// P1 den (0,3)=3: neighbors: (0,2)=2, (0,4)=4, (1,3)=10
// P2 den (8,3)=59: neighbors: (8,2)=58, (8,4)=60(OOB!), (7,3)=52
// col 4 of row 8 = 8*7+4 = 60, but max is 62 (row8,col6). 60 is valid: (8,4)!
// Actually ROWS=9, COLS=7, max idx = 9*7-1=62. (8,4)=60 is valid.
const P1_DEN_NEIGH = [2, 4, 10];
const P2_DEN_NEIGH = [58, 60, 52];

function evaluate(board, side) {
  const { cells, p1Pieces, p2Pieces } = board;
  const opp = OPP[side];

  // Fast terminal checks
  const p2DenCell = cells[59];
  const p1DenCell = cells[3];
  if (p2DenCell !== 0 && cellSide(p2DenCell) === SIDE.PLAYER_1)
    return side === SIDE.PLAYER_1 ? WIN_SCORE : LOSS_SCORE;
  if (p1DenCell !== 0 && cellSide(p1DenCell) === SIDE.PLAYER_2)
    return side === SIDE.PLAYER_2 ? WIN_SCORE : LOSS_SCORE;

  let score = 0;
  const ownDenR = side === SIDE.PLAYER_1 ? 0 : 8;
  // OWN traps: these are where ENEMY pieces get weakened (rank→0) when they invade
  const ownTrapList = side === SIDE.PLAYER_1 ? P1_TRAP_LIST : P2_TRAP_LIST;

  // Pre-compute rat positions from piece lists — avoids order-dependency in the main loop
  const ownRatIdx = (side === SIDE.PLAYER_1 ? board.p1Pieces[0] : board.p2Pieces[0]);
  const oppRatIdx = (side === SIDE.PLAYER_1 ? board.p2Pieces[0] : board.p1Pieces[0]);
  const ownRatInRiver = ownRatIdx >= 0 && isRiver(ownRatIdx);
  const oppRatInRiver = oppRatIdx >= 0 && isRiver(oppRatIdx);

  let totalPieces = 0;
  let ownPieces = 0, oppPieces = 0;
  let minOppDistToDen = 99;

  // Single pass: material + PST + key metrics
  for (let idx = 0; idx < 63; idx++) {
    const c = cells[idx];
    if (c === 0) continue;
    totalPieces++;

    const pSide = cellSide(c);
    const rank  = cellRank(c);
    const r = (idx / 7) | 0;
    const col = idx % 7;

    let pieceScore = MAT[rank] + getPST(rank, r, col, pSide);

    // Trap penalty: piece on enemy's trap territory = rank 0 (can be captured by anything)
    if (isTrap(idx, OPP[pSide])) pieceScore = (pieceScore * 0.15) | 0;

    // Elephant threatened by adjacent enemy rat on land (uses pre-computed positions)
    if (rank === 8 && pSide === side && oppRatIdx >= 0 && !isRiver(oppRatIdx)) {
      const dist = Math.abs(r - ((oppRatIdx / 7) | 0)) + Math.abs(col - oppRatIdx % 7);
      if (dist === 1) pieceScore -= 750;
      else if (dist === 2) pieceScore -= 280;
    }
    if (rank === 8 && pSide === opp && ownRatIdx >= 0 && !isRiver(ownRatIdx)) {
      const dist = Math.abs(r - ((ownRatIdx / 7) | 0)) + Math.abs(col - ownRatIdx % 7);
      if (dist === 1) pieceScore -= 750;
      else if (dist === 2) pieceScore -= 280;
    }

    // Jumper (Lion/Tiger) in jump-ready position: adjacent to river crossing column
    if ((rank === 6 || rank === 7) && !isRiver(idx)) {
      const nextR = pSide === SIDE.PLAYER_1 ? r + 1 : r - 1;
      if (nextR >= 0 && nextR < ROWS && (col === 1 || col === 2 || col === 4 || col === 5)) {
        if (isRiver(nextR * 7 + col)) pieceScore += 35;
      }
    }

    if (pSide === side) {
      score += pieceScore;
      ownPieces++;
    } else {
      score -= pieceScore;
      oppPieces++;
      const distToDen = Math.abs(r - ownDenR) + Math.abs(col - 3);
      if (distToDen < minOppDistToDen) minOppDistToDen = distToDen;
    }
  }

  // ---- Endgame scaling ----
  // When fewer pieces remain, material lead is more decisive — amplify it
  if (totalPieces <= 12) {
    const matLead = (ownPieces - oppPieces) * 280;
    const scale = 1 + (12 - totalPieces) * 0.18;
    score += (matLead * (scale - 1)) | 0;
  }

  // ---- River control ----
  // Rat in river blocks enemy Lion/Tiger jumps; value scales with how many enemy jumpers exist
  const oppLionAlive  = (side === SIDE.PLAYER_1 ? board.p2Pieces[6] : board.p1Pieces[6]) >= 0;
  const oppTigerAlive = (side === SIDE.PLAYER_1 ? board.p2Pieces[5] : board.p1Pieces[5]) >= 0;
  const ownLionAlive  = (side === SIDE.PLAYER_1 ? board.p1Pieces[6] : board.p2Pieces[6]) >= 0;
  const ownTigerAlive = (side === SIDE.PLAYER_1 ? board.p1Pieces[5] : board.p2Pieces[5]) >= 0;
  const oppJumpers = (oppLionAlive ? 1 : 0) + (oppTigerAlive ? 1 : 0);
  const ownJumpers = (ownLionAlive ? 1 : 0) + (ownTigerAlive ? 1 : 0);
  if (ownRatInRiver) score += 50 + oppJumpers * 40;
  if (oppRatInRiver) score -= 50 + ownJumpers * 40;

  // ---- Den threat / defense ----
  if (minOppDistToDen <= 4) {
    const defended = isDenDefended(board, side);
    const rawPenalty = [0, 2200, 800, 320, 110][minOppDistToDen] || 0;
    score -= defended ? (rawPenalty >> 2) : rawPenalty;
    if (defended) score += 180;
  }

  // ---- Direct den attack bonus ----
  // Own piece adjacent to enemy den = immediate win threat
  const ownNeigh = side === SIDE.PLAYER_1 ? P2_DEN_NEIGH : P1_DEN_NEIGH;
  for (const ni of ownNeigh) {
    const c = cells[ni];
    if (c !== 0 && cellSide(c) === side) score += 220;
  }

  // ---- Trap ambush ----
  // Check our own trap squares: enemy pieces here are rank 0 — easy to capture
  for (const [tr, tc] of ownTrapList) {
    const trapIdx = tr * 7 + tc;
    const trapCell = cells[trapIdx];
    if (trapCell !== 0 && cellSide(trapCell) === opp) {
      // Enemy invader on our trap — it's rank 0, can be taken by anything
      if (hasAdjacentFriendly(cells, tr, tc, side)) score += 380;
      else score += 80; // still a target even without immediate capture
    } else if (trapCell !== 0 && cellSide(trapCell) === side) {
      // Our piece blocking our own trap — slight negative (prevents trapping enemies here)
      score -= 20;
    } else if (hasAdjacentFriendly(cells, tr, tc, side)) {
      // Empty trap with adjacent friendly = controlling trap entry
      score += 130;
    }
  }

  return score;
}

function isDenDefended(board, side) {
  const { cells } = board;
  const squares = side === SIDE.PLAYER_1
    ? [[0,2],[0,4],[1,3],[1,2],[1,4],[2,3]]
    : [[8,2],[8,4],[7,3],[7,2],[7,4],[6,3]];
  for (const [r, c] of squares) {
    const cell = cells[r * 7 + c];
    if (cell !== 0 && cellSide(cell) === side) return true;
  }
  return false;
}

function hasAdjacentFriendly(cells, r, c, side) {
  for (const [dr, dc] of [[-1,0],[1,0],[0,-1],[0,1]]) {
    const nr = r + dr, nc = c + dc;
    if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) continue;
    const cell = cells[nr * 7 + nc];
    if (cell !== 0 && cellSide(cell) === side) return true;
  }
  return false;
}

module.exports = { evaluate, WIN_SCORE, LOSS_SCORE, DRAW_SCORE };
