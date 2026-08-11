'use strict';

const SIDE = Object.freeze({ PLAYER_1: 0, PLAYER_2: 1 });
const SIDE_NAME = ['PLAYER_1', 'PLAYER_2'];
const OPP = [1, 0];

// rank: 1..8
const PIECE = Object.freeze({
  RAT:      { rank: 1, name: 'RAT'      },
  CAT:      { rank: 2, name: 'CAT'      },
  DOG:      { rank: 3, name: 'DOG'      },
  WOLF:     { rank: 4, name: 'WOLF'     },
  LEOPARD:  { rank: 5, name: 'LEOPARD'  },
  TIGER:    { rank: 6, name: 'TIGER'    },
  LION:     { rank: 7, name: 'LION'     },
  ELEPHANT: { rank: 8, name: 'ELEPHANT' },
});

// index 0..7 by rank-1
const PIECE_BY_RANK = [
  PIECE.RAT, PIECE.CAT, PIECE.DOG, PIECE.WOLF,
  PIECE.LEOPARD, PIECE.TIGER, PIECE.LION, PIECE.ELEPHANT,
];

const PIECE_BY_NAME = {};
for (const p of Object.values(PIECE)) PIECE_BY_NAME[p.name] = p;

const ROWS = 9, COLS = 7;

// cell encoding: 0 = empty, 1..8 = P1 rank 1..8, 9..16 = P2 rank 1..8
function makeCell(side, rank) { return side === SIDE.PLAYER_1 ? rank : rank + 8; }
function cellSide(c)  { return c <= 8 ? SIDE.PLAYER_1 : SIDE.PLAYER_2; }
function cellRank(c)  { return c <= 8 ? c : c - 8; }
function cellPiece(c) { return PIECE_BY_RANK[cellRank(c) - 1]; }
function isEmpty(c)   { return c === 0; }

// Board layout constants
const P1_DEN_R = 0, P1_DEN_C = 3;
const P2_DEN_R = 8, P2_DEN_C = 3;
const P1_TRAPS = new Set([0*7+2, 0*7+4, 1*7+3]);
const P2_TRAPS = new Set([8*7+2, 8*7+4, 7*7+3]);
const P1_DEN_IDX = P1_DEN_R * 7 + P1_DEN_C;
const P2_DEN_IDX = P2_DEN_R * 7 + P2_DEN_C;

// River: rows 3-5, cols 1-2 and 4-5
const RIVER = new Uint8Array(ROWS * COLS);
for (let r = 3; r <= 5; r++) {
  for (let c of [1, 2, 4, 5]) RIVER[r * 7 + c] = 1;
}

function isRiver(idx) { return RIVER[idx] === 1; }
function isDen(idx, side) { return side === SIDE.PLAYER_1 ? idx === P1_DEN_IDX : idx === P2_DEN_IDX; }
function isEnemyDen(idx, side) { return side === SIDE.PLAYER_1 ? idx === P2_DEN_IDX : idx === P1_DEN_IDX; }
function isTrap(idx, side) {
  return side === SIDE.PLAYER_1 ? P1_TRAPS.has(idx) : P2_TRAPS.has(idx);
}

// --- Zobrist ---
const ZOBRIST = (() => {
  // [row][col][side][rank-1] => [lo, hi] (two 32-bit)
  const rng = (() => {
    let s = 0xdeadbeef;
    return () => { s ^= s << 13; s ^= s >>> 17; s ^= s << 5; return s >>> 0; };
  })();
  const table = new Array(ROWS * COLS * 2 * 8 * 2);
  for (let i = 0; i < table.length; i++) table[i] = rng();
  // side_to_move[side] [lo, hi]
  const stm = [rng(), rng(), rng(), rng()];
  return { table, stm };
})();

function zobristIdx(idx, side, rank) {
  return (idx * 16 + side * 8 + (rank - 1)) * 2;
}

// --- Board ---
class Board {
  constructor() {
    this.cells = new Uint8Array(ROWS * COLS);
    this.hashLo = 0;
    this.hashHi = 0;
    // piece lists: p1Pieces[rank-1] = idx or -1, p2Pieces same
    this.p1Pieces = new Int8Array(8).fill(-1);
    this.p2Pieces = new Int8Array(8).fill(-1);
    // track multiple pieces of same rank is not possible in Jungle Chess (1 of each)
  }

  static create() {
    const b = new Board();
    // PLAYER_1 (top)
    b._place(0, 0, SIDE.PLAYER_1, 7); // LION
    b._place(0, 6, SIDE.PLAYER_1, 6); // TIGER
    b._place(1, 1, SIDE.PLAYER_1, 3); // DOG
    b._place(1, 5, SIDE.PLAYER_1, 2); // CAT
    b._place(2, 0, SIDE.PLAYER_1, 1); // RAT
    b._place(2, 2, SIDE.PLAYER_1, 5); // LEOPARD
    b._place(2, 4, SIDE.PLAYER_1, 4); // WOLF
    b._place(2, 6, SIDE.PLAYER_1, 8); // ELEPHANT
    // PLAYER_2 (bottom)
    b._place(8, 6, SIDE.PLAYER_2, 7); // LION
    b._place(8, 0, SIDE.PLAYER_2, 6); // TIGER
    b._place(7, 5, SIDE.PLAYER_2, 3); // DOG
    b._place(7, 1, SIDE.PLAYER_2, 2); // CAT
    b._place(6, 6, SIDE.PLAYER_2, 1); // RAT
    b._place(6, 4, SIDE.PLAYER_2, 5); // LEOPARD
    b._place(6, 2, SIDE.PLAYER_2, 4); // WOLF
    b._place(6, 0, SIDE.PLAYER_2, 8); // ELEPHANT
    return b;
  }

  _place(r, c, side, rank) {
    const idx = r * 7 + c;
    const cell = makeCell(side, rank);
    this.cells[idx] = cell;
    const z = zobristIdx(idx, side, rank);
    this.hashLo ^= ZOBRIST.table[z];
    this.hashHi ^= ZOBRIST.table[z + 1];
    if (side === SIDE.PLAYER_1) this.p1Pieces[rank - 1] = idx;
    else this.p2Pieces[rank - 1] = idx;
  }

  get(idx)   { return this.cells[idx]; }
  getRC(r,c) { return this.cells[r * 7 + c]; }

  _addHash(idx, side, rank) {
    const z = zobristIdx(idx, side, rank);
    this.hashLo ^= ZOBRIST.table[z];
    this.hashHi ^= ZOBRIST.table[z + 1];
  }

  makeMove(move) {
    const { from, to, movedRank, movedSide, capRank, capSide } = move;
    // remove from
    this.cells[from] = 0;
    this._addHash(from, movedSide, movedRank);
    // remove capture
    if (capRank > 0) {
      this._addHash(to, capSide, capRank);
      if (capSide === SIDE.PLAYER_1) this.p1Pieces[capRank - 1] = -1;
      else this.p2Pieces[capRank - 1] = -1;
    }
    // place at to
    this.cells[to] = makeCell(movedSide, movedRank);
    this._addHash(to, movedSide, movedRank);
    if (movedSide === SIDE.PLAYER_1) this.p1Pieces[movedRank - 1] = to;
    else this.p2Pieces[movedRank - 1] = to;
  }

  undoMove(move) {
    const { from, to, movedRank, movedSide, capRank, capSide } = move;
    // remove from to
    this.cells[to] = 0;
    this._addHash(to, movedSide, movedRank);
    // restore moved piece at from
    this.cells[from] = makeCell(movedSide, movedRank);
    this._addHash(from, movedSide, movedRank);
    if (movedSide === SIDE.PLAYER_1) this.p1Pieces[movedRank - 1] = from;
    else this.p2Pieces[movedRank - 1] = from;
    // restore captured
    if (capRank > 0) {
      this.cells[to] = makeCell(capSide, capRank);
      this._addHash(to, capSide, capRank);
      if (capSide === SIDE.PLAYER_1) this.p1Pieces[capRank - 1] = to;
      else this.p2Pieces[capRank - 1] = to;
    }
  }

  hashKey(side) {
    const sIdx = side * 2;
    const lo = (this.hashLo ^ ZOBRIST.stm[sIdx]) | 0;
    const hi = (this.hashHi ^ ZOBRIST.stm[sIdx + 1]) | 0;
    return (lo ^ hi) | 0; // fold both 32-bit halves into one key
  }

  // For repetition tracking — string key avoids float precision loss
  posKey(side) {
    const lo = (this.hashLo ^ ZOBRIST.stm[side * 2]) >>> 0;
    const hi = (this.hashHi ^ ZOBRIST.stm[side * 2 + 1]) >>> 0;
    return `${hi},${lo}`;
  }

  clone() {
    const b = new Board();
    b.cells.set(this.cells);
    b.hashLo = this.hashLo;
    b.hashHi = this.hashHi;
    b.p1Pieces.set(this.p1Pieces);
    b.p2Pieces.set(this.p2Pieces);
    return b;
  }

  // Parse from server board array
  static fromArray(arr) {
    const b = new Board();
    for (let r = 0; r < ROWS; r++) {
      for (let c = 0; c < COLS; c++) {
        const cell = arr[r]?.[c];
        if (!cell) continue;
        const under = cell.lastIndexOf('_');
        const sideName = cell.slice(0, under);
        const pieceName = cell.slice(under + 1);
        const side = sideName === 'PLAYER_1' ? SIDE.PLAYER_1 : SIDE.PLAYER_2;
        const piece = PIECE_BY_NAME[pieceName];
        if (!piece) continue;
        b._place(r, c, side, piece.rank);
      }
    }
    return b;
  }
}

// Move is a plain object for speed
function makeMove(from, to, movedRank, movedSide, capRank = 0, capSide = 0) {
  return { from, to, movedRank, movedSide, capRank, capSide };
}

module.exports = {
  SIDE, SIDE_NAME, OPP, PIECE, PIECE_BY_RANK, PIECE_BY_NAME,
  ROWS, COLS, RIVER, P1_DEN_R, P1_DEN_C, P2_DEN_R, P2_DEN_C,
  P1_DEN_IDX, P2_DEN_IDX, P1_TRAPS, P2_TRAPS,
  isRiver, isDen, isEnemyDen, isTrap, isEmpty, cellSide, cellRank, cellPiece, makeCell,
  Board, makeMove,
};
