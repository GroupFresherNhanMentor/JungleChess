import { Injectable } from '@angular/core';
import { Move, Piece, PieceSide, PieceType, Position, PIECE_RANKS } from '../models/game.models';
import { ServerMoveRecord } from '../models/room-events.models';

@Injectable({ providedIn: 'root' })
export class BoardAdapterService {

  // "PLAYER_1" → 1 (Red/top), "PLAYER_2" → 0 (Blue/bottom)
  toSide(backendSide: string): PieceSide {
    return backendSide === 'PLAYER_1' ? 1 : 0;
  }

  // 1 → "PLAYER_1", 0 → "PLAYER_2"
  toBackendSide(side: PieceSide): string {
    return side === 1 ? 'PLAYER_1' : 'PLAYER_2';
  }

  // Convert board[row][col] = "PLAYER_1_LION" to Piece[]
  toPieces(board: string[][]): Piece[] {
    const pieces: Piece[] = [];
    for (let row = 0; row < board.length; row++) {
      for (let col = 0; col < board[row].length; col++) {
        const code = board[row][col];
        if (!code) continue;
        const idx = code.lastIndexOf('_');
        const sideStr = code.substring(0, idx);
        const typeStr = code.substring(idx + 1).toLowerCase() as PieceType;
        const side = this.toSide(sideStr);
        pieces.push({
          id: `${sideStr}_${typeStr}`,
          type: typeStr,
          side,
          rank: PIECE_RANKS[typeStr] ?? 0,
          position: { col, row },
        });
      }
    }
    return pieces;
  }

  // Convert frontend Position to backend [row, col] array (row-first)
  toBackendPos(pos: Position): [number, number] {
    return [pos.row, pos.col];
  }

  // Convert backend MoveRecord to frontend Move
  toMove(record: ServerMoveRecord): Move {
    const [fromRow, fromCol] = record.from;
    const [toRow, toCol] = record.to;
    const fromPos: Position = { col: fromCol, row: fromRow };
    const toPos: Position = { col: toCol, row: toRow };

    const idx = record.movedPiece.lastIndexOf('_');
    const sideStr = record.movedPiece.substring(0, idx);
    const typeStr = record.movedPiece.substring(idx + 1).toLowerCase() as PieceType;

    const piece: Piece = {
      id: `${sideStr}_${typeStr}`,
      type: typeStr,
      side: this.toSide(sideStr),
      rank: PIECE_RANKS[typeStr] ?? 0,
      position: toPos,
    };

    return { from: fromPos, to: toPos, piece };
  }
}
