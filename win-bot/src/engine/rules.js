'use strict';

const {
  SIDE, OPP, ROWS, COLS, isRiver, isDen, isEnemyDen, isTrap,
  isEmpty, cellSide, cellRank, makeCell, Board, makeMove,
} = require('./model');

const DIRS = [-7, 7, -1, 1]; // up, down, left, right (as flat index deltas)
const EDGE_LEFT  = new Uint8Array(ROWS * COLS); // 1 if col==0
const EDGE_RIGHT = new Uint8Array(ROWS * COLS); // 1 if col==6

for (let r = 0; r < ROWS; r++) {
  EDGE_LEFT[r * 7]     = 1;
  EDGE_RIGHT[r * 7 + 6] = 1;
}

function canCapture(atkRank, atkSide, fromIdx, defRank, defSide, toIdx, cells) {
  if (defRank === 0) return true; // empty
  if (defSide === atkSide) return false;

  // Defender on enemy trap = effective rank 0 (attacked in attacker's trap territory)
  if (isTrap(toIdx, atkSide)) return true;

  // Rat in river cannot capture land piece
  if (isRiver(fromIdx) && !isRiver(toIdx)) return false;
  // Land piece cannot capture piece in river
  if (!isRiver(fromIdx) && isRiver(toIdx)) return false;

  // RAT vs ELEPHANT special
  if (atkRank === 1 && defRank === 8) return true;
  if (atkRank === 8 && defRank === 1) return false;

  return atkRank >= defRank;
}

function jumpTarget(fromIdx, dr_flat, cells) {
  let cur = fromIdx + dr_flat;
  const leftOk  = dr_flat !== -1;  // going right won't wrap left
  const rightOk = dr_flat !== 1;
  while (cur >= 0 && cur < ROWS * COLS && isRiver(cur)) {
    // edge wrap guard
    if (dr_flat === -1 && EDGE_LEFT[cur + 1])  return -1;
    if (dr_flat ===  1 && EDGE_RIGHT[cur - 1]) return -1;
    if (cells[cur] !== 0) return -1; // RAT blocking
    cur += dr_flat;
  }
  if (cur < 0 || cur >= ROWS * COLS) return -1;
  // Validate no row-wrap for horizontal moves
  if (dr_flat === -1 || dr_flat === 1) {
    const fromRow = (fromIdx / 7) | 0;
    const toRow   = (cur / 7) | 0;
    if (fromRow !== toRow) return -1;
  }
  return cur;
}

function getMoves(board, side) {
  const { cells } = board;
  const moves = [];
  const opp = OPP[side];

  for (let from = 0; from < ROWS * COLS; from++) {
    const c = cells[from];
    if (c === 0) continue;
    if (cellSide(c) !== side) continue;

    const rank = cellRank(c);
    const fromR = (from / 7) | 0;
    const fromC = from % 7;

    for (let d = 0; d < 4; d++) {
      const delta = DIRS[d];

      // Edge boundary
      if (delta === -1 && fromC === 0) continue;
      if (delta ===  1 && fromC === 6) continue;

      let to = from + delta;
      if (to < 0 || to >= ROWS * COLS) continue;

      // Cannot enter own den
      if (isDen(to, side)) continue;

      if (isRiver(to)) {
        if (rank === 1) {
          // RAT can enter river
          const dc = cells[to];
          if (dc === 0 || (cellSide(dc) !== side && canCapture(rank, side, from, cellRank(dc), cellSide(dc), to, cells))) {
            const capRank = dc === 0 ? 0 : cellRank(dc);
            const capSide = dc === 0 ? 0 : cellSide(dc);
            moves.push(makeMove(from, to, rank, side, capRank, capSide));
          }
        } else if (rank === 6 || rank === 7) {
          // TIGER (6) or LION (7) jump
          const landIdx = jumpTarget(from, delta, cells);
          if (landIdx === -1) continue;
          if (isDen(landIdx, side)) continue;
          const dc = cells[landIdx];
          const defRank = dc === 0 ? 0 : cellRank(dc);
          const defSide = dc === 0 ? 0 : cellSide(dc);
          if (dc === 0 || (defSide !== side && canCapture(rank, side, from, defRank, defSide, landIdx, cells))) {
            moves.push(makeMove(from, landIdx, rank, side, defRank, dc === 0 ? 0 : defSide));
          }
        }
        // other pieces can't enter river
      } else {
        // Normal land move
        const dc = cells[to];
        if (dc === 0) {
          moves.push(makeMove(from, to, rank, side, 0, 0));
        } else {
          const defRank = cellRank(dc);
          const defSide = cellSide(dc);
          if (defSide !== side && canCapture(rank, side, from, defRank, defSide, to, cells)) {
            moves.push(makeMove(from, to, rank, side, defRank, defSide));
          }
        }
      }
    }
  }
  return moves;
}

function checkWinner(board) {
  const { cells } = board;
  const p2DenCell = cells[59]; // P2 den at (8,3)
  if (p2DenCell !== 0 && cellSide(p2DenCell) === SIDE.PLAYER_1) return SIDE.PLAYER_1;
  const p1DenCell = cells[3];
  if (p1DenCell !== 0 && cellSide(p1DenCell) === SIDE.PLAYER_2) return SIDE.PLAYER_2;

  // Check if a side has no pieces left
  let p1Count = 0, p2Count = 0;
  for (let i = 0; i < 8; i++) {
    if (board.p1Pieces[i] >= 0) p1Count++;
    if (board.p2Pieces[i] >= 0) p2Count++;
  }
  if (p1Count === 0) return SIDE.PLAYER_2;
  if (p2Count === 0) return SIDE.PLAYER_1;
  return null;
}

module.exports = { getMoves, checkWinner, canCapture };
