import { GameRuleService } from './game-rule.service';
import { PIECE_RANKS, Piece, PieceSide, PieceType, Position } from '../models/game.models';

describe('GameRuleService', () => {
  const service = new GameRuleService();

  const createPiece = (
    type: PieceType,
    side: PieceSide,
    position: Position
  ): Piece => ({
    id: `${side}-${type}-${position.col}-${position.row}`,
    type,
    side,
    rank: PIECE_RANKS[type],
    position
  });

  it('creates the canonical terrain and sixteen-piece setup', () => {
    const pieces = service.getInitialPieces();

    expect(pieces).toHaveLength(16);
    expect(service.getTileInfo(1, 3).type).toBe('water');
    expect(service.getTileInfo(0, 3).type).toBe('land');
    expect(service.getTileInfo(3, 0)).toEqual({ col: 3, row: 0, type: 'den', side: 1 });
    expect(service.getPieceAt(pieces, 0, 0)?.type).toBe('lion');
    expect(service.getPieceAt(pieces, 0, 8)?.type).toBe('tiger');
  });

  it('allows Rat but not Cat to enter river cells', () => {
    const rat = createPiece('rat', 0, { col: 0, row: 3 });
    const cat = createPiece('cat', 0, { col: 0, row: 3 });

    expect(service.getValidMoves(rat, [rat])).toContainEqual({ col: 1, row: 3 });
    expect(service.getValidMoves(cat, [cat])).not.toContainEqual({ col: 1, row: 3 });
  });

  it('allows Lion to jump a clear river and blocks the jump with a Rat', () => {
    const lion = createPiece('lion', 0, { col: 0, row: 3 });
    const ratInRiver = createPiece('rat', 1, { col: 2, row: 3 });

    expect(service.getValidMoves(lion, [lion])).toContainEqual({ col: 3, row: 3 });
    expect(service.getValidMoves(lion, [lion, ratInRiver])).not.toContainEqual({ col: 3, row: 3 });
  });

  it('applies trap ranks and Rat water-capture restrictions', () => {
    const redCat = createPiece('cat', 1, { col: 2, row: 1 });
    const blueElephantInRedTrap = createPiece('elephant', 0, { col: 2, row: 0 });
    const blueElephantInRedTrapAttacking = createPiece('elephant', 0, { col: 2, row: 0 });
    const redRat = createPiece('rat', 1, { col: 2, row: 1 });
    const ratInWater = createPiece('rat', 1, { col: 1, row: 3 });
    const elephantOnLand = createPiece('elephant', 0, { col: 1, row: 2 });

    expect(service.canCapture(redCat, blueElephantInRedTrap)).toBe(true);
    expect(service.canCapture(blueElephantInRedTrapAttacking, redRat)).toBe(false);
    expect(service.canCapture(ratInWater, elephantOnLand)).toBe(false);
  });

  it('reports a den victory and a no-legal-move victory', () => {
    const bluePieceInRedDen = createPiece('rat', 0, { col: 3, row: 0 });
    const onlyRedPiece = createPiece('rat', 1, { col: 0, row: 0 });

    expect(service.checkWinCondition([bluePieceInRedDen], 1)).toEqual({
      gameOver: true,
      winner: 0,
      reason: 'den'
    });
    expect(service.checkWinCondition([onlyRedPiece], 0)).toEqual({
      gameOver: true,
      winner: 1,
      reason: 'no_moves'
    });
  });
});
