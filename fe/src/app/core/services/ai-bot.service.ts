import { Injectable } from '@angular/core';
import { Piece, PieceSide, Position } from '../models/game.models';
import { GameRuleService } from './game-rule.service';

export interface BestMoveResult {
  from: Position;
  to: Position;
  depthAchieved: number;
}

@Injectable({
  providedIn: 'root'
})
export class AiBotService {
  constructor(private ruleService: GameRuleService) {}

  public computeMove(
    pieces: Piece[],
    botSide: PieceSide,
    targetDepth: number,
    timeLimitMs: number
  ): Promise<BestMoveResult | null> {
    return new Promise((resolve) => {
      const startTime = Date.now();
      let bestMove: { from: Position; to: Position } | null = null;
      let actualDepth = 1;

      // Iterative deepening search up to targetDepth
      for (let depth = 1; depth <= targetDepth; depth++) {
        if (Date.now() - startTime > timeLimitMs) {
          break;
        }

        const result = this.alphaBetaSearch(
          pieces,
          depth,
          -Infinity,
          Infinity,
          true,
          botSide,
          startTime,
          timeLimitMs
        );

        if (result.move) {
          bestMove = result.move;
          actualDepth = depth;
        }
      }

      if (!bestMove) {
        // Fallback: Pick any valid move
        const botPieces = pieces.filter((p) => p.side === botSide);
        for (const p of botPieces) {
          const moves = this.ruleService.getValidMoves(p, pieces);
          if (moves.length > 0) {
            bestMove = { from: p.position, to: moves[0] };
            break;
          }
        }
      }

      if (bestMove) {
        resolve({
          from: bestMove.from,
          to: bestMove.to,
          depthAchieved: actualDepth
        });
      } else {
        resolve(null);
      }
    });
  }

  private alphaBetaSearch(
    pieces: Piece[],
    depth: number,
    alpha: number,
    beta: number,
    isMaximizing: boolean,
    botSide: PieceSide,
    startTime: number,
    timeLimitMs: number
  ): { score: number; move?: { from: Position; to: Position } } {
    if (depth === 0 || Date.now() - startTime > timeLimitMs) {
      return { score: this.evaluateBoard(pieces, botSide) };
    }

    const currentSide: PieceSide = isMaximizing
      ? botSide
      : ((1 - botSide) as PieceSide);

    // Check game over
    const winStatus = this.ruleService.checkWinCondition(pieces, currentSide);
    if (winStatus.gameOver) {
      if (winStatus.winner === botSide) {
        return { score: 10000 + depth };
      } else if (winStatus.winner !== undefined) {
        return { score: -10000 - depth };
      }
      return { score: 0 };
    }

    const sidePieces = pieces.filter((p) => p.side === currentSide);
    let bestMove: { from: Position; to: Position } | undefined = undefined;

    if (isMaximizing) {
      let maxScore = -Infinity;
      for (const p of sidePieces) {
        const moves = this.ruleService.getValidMoves(p, pieces);
        for (const m of moves) {
          const simulated = this.simulateMove(pieces, p.position, m);
          const evalResult = this.alphaBetaSearch(
            simulated,
            depth - 1,
            alpha,
            beta,
            false,
            botSide,
            startTime,
            timeLimitMs
          );

          if (evalResult.score > maxScore) {
            maxScore = evalResult.score;
            bestMove = { from: p.position, to: m };
          }
          alpha = Math.max(alpha, maxScore);
          if (beta <= alpha) {
            break;
          }
        }
      }
      return { score: maxScore, move: bestMove };
    } else {
      let minScore = Infinity;
      for (const p of sidePieces) {
        const moves = this.ruleService.getValidMoves(p, pieces);
        for (const m of moves) {
          const simulated = this.simulateMove(pieces, p.position, m);
          const evalResult = this.alphaBetaSearch(
            simulated,
            depth - 1,
            alpha,
            beta,
            true,
            botSide,
            startTime,
            timeLimitMs
          );

          if (evalResult.score < minScore) {
            minScore = evalResult.score;
            bestMove = { from: p.position, to: m };
          }
          beta = Math.min(beta, minScore);
          if (beta <= alpha) {
            break;
          }
        }
      }
      return { score: minScore, move: bestMove };
    }
  }

  private simulateMove(pieces: Piece[], from: Position, to: Position): Piece[] {
    return pieces
      .filter((p) => !(p.position.col === to.col && p.position.row === to.row))
      .map((p) => {
        if (p.position.col === from.col && p.position.row === from.row) {
          return {
            ...p,
            position: { col: to.col, row: to.row }
          };
        }
        return p;
      });
  }

  public evaluateBoard(pieces: Piece[], botSide: PieceSide): number {
    let botScore = 0;
    let enemyScore = 0;

    const enemyDenCol = 3;
    const enemyDenRow = botSide === 0 ? 0 : 8; // Blue's target is Red's den (0, 3)

    for (const p of pieces) {
      // Base rank score
      let pieceValue = p.rank * 10;

      // Bonus for distance to enemy den
      const distToDen =
        Math.abs(p.position.col - enemyDenCol) + Math.abs(p.position.row - enemyDenRow);
      pieceValue += (14 - distToDen) * 2;

      if (p.side === botSide) {
        botScore += pieceValue;
      } else {
        enemyScore += pieceValue;
      }
    }

    return botScore - enemyScore;
  }
}
