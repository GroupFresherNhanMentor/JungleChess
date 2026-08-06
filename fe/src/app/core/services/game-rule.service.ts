import { Injectable } from '@angular/core';
import {
  Piece,
  PieceSide,
  PieceType,
  PIECE_RANKS,
  Position,
  GameResult,
  Tile,
  TileType
} from '../models/game.models';

@Injectable({
  providedIn: 'root'
})
export class GameRuleService {
  public readonly COLS = 7;
  public readonly ROWS = 9;

  // Define tiles
  public isWaterTile(col: number, row: number): boolean {
    return (
      (row >= 3 && row <= 5 && (col === 1 || col === 2)) ||
      (row >= 3 && row <= 5 && (col === 4 || col === 5))
    );
  }

  public getTileInfo(col: number, row: number): Tile {
    if (this.isWaterTile(col, row)) {
      return { col, row, type: 'water' };
    }
    // Dens
    if (col === 3 && row === 0) {
      return { col, row, type: 'den', side: 1 }; // Red den (Player 1, D9)
    }
    if (col === 3 && row === 8) {
      return { col, row, type: 'den', side: 0 }; // Blue den (Player 0, D1)
    }
    // Red Traps (Player 1) around D9
    if (
      (col === 2 && row === 0) ||
      (col === 4 && row === 0) ||
      (col === 3 && row === 1)
    ) {
      return { col, row, type: 'trap', side: 1 };
    }
    // Blue Traps (Player 0) around D1
    if (
      (col === 2 && row === 8) ||
      (col === 4 && row === 8) ||
      (col === 3 && row === 7)
    ) {
      return { col, row, type: 'trap', side: 0 };
    }
    return { col, row, type: 'land' };
  }

  public getInitialPieces(): Piece[] {
    const pieces: Piece[] = [];

    // Player 1 (Red) - Top half (Rows 9, 8, 7 => index 0, 1, 2)
    const redSetup: { type: PieceType; col: number; row: number }[] = [
      { type: 'lion', col: 0, row: 0 },    // A9
      { type: 'tiger', col: 6, row: 0 },   // G9
      { type: 'dog', col: 1, row: 1 },     // B8
      { type: 'cat', col: 5, row: 1 },     // F8
      { type: 'rat', col: 0, row: 2 },     // A7
      { type: 'leopard', col: 2, row: 2 }, // C7
      { type: 'wolf', col: 4, row: 2 },    // E7
      { type: 'elephant', col: 6, row: 2 } // G7
    ];

    // Player 0 (Blue) - Bottom half (Rows 3, 2, 1 => index 6, 7, 8)
    const blueSetup: { type: PieceType; col: number; row: number }[] = [
      { type: 'elephant', col: 0, row: 6 },// A3
      { type: 'wolf', col: 2, row: 6 },    // C3
      { type: 'leopard', col: 4, row: 6 }, // E3
      { type: 'rat', col: 6, row: 6 },     // G3
      { type: 'cat', col: 1, row: 7 },     // B2
      { type: 'dog', col: 5, row: 7 },     // F2
      { type: 'tiger', col: 0, row: 8 },   // A1
      { type: 'lion', col: 6, row: 8 }     // G1
    ];

    blueSetup.forEach((item, index) => {
      pieces.push({
        id: `p0_${item.type}_${index}`,
        type: item.type,
        side: 0,
        rank: PIECE_RANKS[item.type],
        position: { col: item.col, row: item.row }
      });
    });

    redSetup.forEach((item, index) => {
      pieces.push({
        id: `p1_${item.type}_${index}`,
        type: item.type,
        side: 1,
        rank: PIECE_RANKS[item.type],
        position: { col: item.col, row: item.row }
      });
    });

    return pieces;
  }

  public getPieceAt(pieces: Piece[], col: number, row: number): Piece | undefined {
    return pieces.find((p) => p.position.col === col && p.position.row === row);
  }

  public getValidMoves(piece: Piece, pieces: Piece[]): Position[] {
    const validMoves: Position[] = [];
    const { col, row } = piece.position;
    const directions = [
      { col: 0, row: -1 }, // Up
      { col: 0, row: 1 },  // Down
      { col: -1, row: 0 }, // Left
      { col: 1, row: 0 }   // Right
    ];

    for (const dir of directions) {
      const nextCol = col + dir.col;
      const nextRow = row + dir.row;

      if (nextCol < 0 || nextCol >= this.COLS || nextRow < 0 || nextRow >= this.ROWS) {
        continue;
      }

      const targetTile = this.getTileInfo(nextCol, nextRow);

      // Cannot enter own den
      if (targetTile.type === 'den' && targetTile.side === piece.side) {
        continue;
      }

      // Water tile logic
      if (targetTile.type === 'water') {
        if (piece.type === 'rat') {
          // Rat can enter water
          const occupant = this.getPieceAt(pieces, nextCol, nextRow);
          if (!occupant) {
            validMoves.push({ col: nextCol, row: nextRow });
          } else if (occupant.side !== piece.side && this.canCapture(piece, occupant)) {
            validMoves.push({ col: nextCol, row: nextRow });
          }
        } else if (piece.type === 'lion' || piece.type === 'tiger') {
          // Lion and Tiger can jump across river
          const landingPos = this.getRiverJumpLanding(piece, dir, pieces);
          if (landingPos) {
            const occupant = this.getPieceAt(pieces, landingPos.col, landingPos.row);
            if (!occupant || this.canCapture(piece, occupant)) {
              validMoves.push(landingPos);
            }
          }
        }
        continue;
      }

      // Normal land/trap/enemy den move
      const occupant = this.getPieceAt(pieces, nextCol, nextRow);
      if (!occupant) {
        validMoves.push({ col: nextCol, row: nextRow });
      } else if (occupant.side !== piece.side) {
        if (this.canCapture(piece, occupant)) {
          validMoves.push({ col: nextCol, row: nextRow });
        }
      }
    }

    return validMoves;
  }

  private getRiverJumpLanding(
    piece: Piece,
    dir: { col: number; row: number },
    pieces: Piece[]
  ): Position | null {
    let currCol = piece.position.col + dir.col;
    let currRow = piece.position.row + dir.row;

    // Check if river jump pathway is blocked by rat
    while (
      currCol >= 0 &&
      currCol < this.COLS &&
      currRow >= 0 &&
      currRow < this.ROWS &&
      this.isWaterTile(currCol, currRow)
    ) {
      const ratOccupant = this.getPieceAt(pieces, currCol, currRow);
      if (ratOccupant) {
        // Water is blocked by rat
        return null;
      }
      currCol += dir.col;
      currRow += dir.row;
    }

    if (
      currCol >= 0 &&
      currCol < this.COLS &&
      currRow >= 0 &&
      currRow < this.ROWS &&
      !this.isWaterTile(currCol, currRow)
    ) {
      // Cannot land on own den
      const landingTile = this.getTileInfo(currCol, currRow);
      if (landingTile.type === 'den' && landingTile.side === piece.side) {
        return null;
      }
      return { col: currCol, row: currRow };
    }

    return null;
  }

  public canCapture(attacker: Piece, defender: Piece): boolean {
    if (attacker.side === defender.side) {
      return false;
    }

    const attackerTile = this.getTileInfo(attacker.position.col, attacker.position.row);
    const defenderTile = this.getTileInfo(defender.position.col, defender.position.row);

    const attackerRank = this.getEffectiveRank(attacker, attackerTile);
    const defenderRank = this.getEffectiveRank(defender, defenderTile);

    // A piece trapped by its opponent loses its capture power.
    if (attackerRank === 0) {
      return false;
    }

    // A Rat in water can only capture another Rat in water.
    if (attackerTile.type === 'water' && defenderTile.type !== 'water') {
      return false;
    }

    // Special rule: Rat captures Elephant
    if (attacker.type === 'rat' && defender.type === 'elephant') {
      return attackerTile.type !== 'water'; // Only if rat is on land
    }

    // Special rule: Elephant cannot capture Rat
    if (attacker.type === 'elephant' && defender.type === 'rat') {
      return false;
    }

    // General rank comparison
    return attackerRank >= defenderRank;
  }

  public checkWinCondition(
    pieces: Piece[],
    currentTurnSide: PieceSide
  ): GameResult {
    // 1. Check if any piece entered enemy den
    const redDenOccupant = this.getPieceAt(pieces, 3, 0);
    if (redDenOccupant && redDenOccupant.side === 0) {
      return { gameOver: true, winner: 0, reason: 'den' }; // Blue entered Red den
    }

    const blueDenOccupant = this.getPieceAt(pieces, 3, 8);
    if (blueDenOccupant && blueDenOccupant.side === 1) {
      return { gameOver: true, winner: 1, reason: 'den' }; // Red entered Blue den
    }

    // 2. Check if current turn player has any valid moves.
    const currentSidePieces = pieces.filter((p) => p.side === currentTurnSide);
    let hasMoves = false;

    for (const p of currentSidePieces) {
      const valid = this.getValidMoves(p, pieces);
      if (valid.length > 0) {
        hasMoves = true;
        break;
      }
    }

    if (!hasMoves) {
      const winner: PieceSide = currentTurnSide === 0 ? 1 : 0;
      return { gameOver: true, winner, reason: 'no_moves' };
    }

    return { gameOver: false };
  }

  private getEffectiveRank(piece: Piece, tile: Tile): number {
    return tile.type === 'trap' && tile.side !== piece.side ? 0 : piece.rank;
  }
}
