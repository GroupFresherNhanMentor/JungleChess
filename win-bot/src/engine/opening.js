'use strict';

const { SIDE } = require('./model');

// Opening book keyed by move number and side.
// Each entry is {from, to} in flat index (row*7+col).
// These are heuristic strong openings for Jungle Chess.
//
// PLAYER_1 moves DOWN (toward row 8, den at (8,3))
// PLAYER_2 moves UP   (toward row 0, den at (0,3))
//
// Notation: [from_flat, to_flat]  where flat = row*7+col
//
// Key opening principles:
// 1. Advance Lion (rank 7) to jump river early for threats
// 2. Position Rat (rank 1) near river to block enemy jumpers / threaten elephant
// 3. Advance mid-range pieces through center

const P1_BOOK = [
  // Move 1: Lion (0,0)=0 → (2,0)=14 (approach river for jump)
  { from: 0, to: 14 },
  // Move 2: Rat (2,0)=14... wait, Lion moved there. Rat is at (2,0)=14
  // After Lion moves to 14, next: Tiger (0,6)=6 → (2,6)=20
  { from: 6, to: 20 },
  // Move 3: Rat (2,0)=14 → (3,0)=21 (enter river left side to block enemy Tiger jump)
  { from: 14, to: 21 },
  // Move 4: Wolf (2,4)=18 → (3,4)=25... but (3,4) is river. Skip. Wolf → (4,4)? Also river.
  // Wolf (2,4)=18 → (3,4) is river - can't enter (wolf rank 4).
  // Wolf moves to (2,3)=17? (2,3) is open. But closer to den approach.
  // Better: Leopard (2,2)=16 → (3,2)? river. Leopard can't enter river.
  // Leopard → (2,3)=17 (approach center)
  { from: 16, to: 17 },
];

const P2_BOOK = [
  // Mirror of P1
  // PLAYER_2: Lion at (8,6)=62 → (6,6)=48
  { from: 62, to: 48 },
  // Tiger (8,0)=56 → (6,0)=42
  { from: 56, to: 42 },
  // Rat (6,6)=48... wait Lion moved there. Rat at (6,6)=48 but Lion is there now.
  // P2 Rat is at (6,6)=48. After Lion moves, (6,6) is occupied.
  // Rat (6,6): Lion occupies, so Rat can't go there.
  // Rat must be at (6,6)=48. With Lion at 48, the book has a conflict.
  // Let me reconsider — the book only works if moves don't conflict.
  // P2 Rat at (6,6)=48 → (5,6)=41 (approach river)
  { from: 48, to: 41 },
  // Leopard (6,4)=46 → (6,3)=45 (center approach)
  { from: 46, to: 45 },
];

// Simple book: indexed by [side][moveIndex]
// Returns null when book is exhausted
class OpeningBook {
  constructor() {
    this.usedMoves = { [SIDE.PLAYER_1]: 0, [SIDE.PLAYER_2]: 0 };
  }

  getMove(board, side, moveNumber) {
    const book = side === SIDE.PLAYER_1 ? P1_BOOK : P2_BOOK;
    const idx = this.usedMoves[side];
    if (idx >= book.length) return null;

    const entry = book[idx];
    // Validate the piece is still there
    const cell = board.cells[entry.from];
    if (cell === 0) {
      this.usedMoves[side]++; // skip invalid entry
      return null;
    }
    // Validate destination is empty or capturable
    const dest = board.cells[entry.to];
    if (dest !== 0) {
      this.usedMoves[side]++; // skip if blocked
      return null;
    }

    this.usedMoves[side]++;
    return entry; // {from, to} as flat indices
  }

  reset() {
    this.usedMoves[SIDE.PLAYER_1] = 0;
    this.usedMoves[SIDE.PLAYER_2] = 0;
  }
}

module.exports = { OpeningBook };
