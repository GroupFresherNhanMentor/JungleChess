package fpt.qn.junglechess.game.bot.eval;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Enhanced BoardEvaluator for Jungle Chess.
 * Evaluates Material, Specialized Piece-Square Tables (PST), Enemy Trap Control,
 * Home Trap Ambush Defense, Den Defense, and Rat-Elephant dynamics.
 */
@Component
public class BoardEvaluator {

    public static final int WIN_SCORE = 100000;
    public static final int LOSS_SCORE = -100000;

    public BoardEvaluator() {
    }

    public static final Map<PieceType, Integer> PIECE_VALUES = new EnumMap<>(PieceType.class);

    static {
        PIECE_VALUES.put(PieceType.RAT, 100);
        PIECE_VALUES.put(PieceType.CAT, 200);
        PIECE_VALUES.put(PieceType.DOG, 300);
        PIECE_VALUES.put(PieceType.WOLF, 400);
        PIECE_VALUES.put(PieceType.LEOPARD, 500);
        PIECE_VALUES.put(PieceType.TIGER, 600);
        PIECE_VALUES.put(PieceType.LION, 700);
        PIECE_VALUES.put(PieceType.ELEPHANT, 800);
    }

    // Default PST (PLAYER_1 perspective: row 0 = home, row 8 = enemy)
    private static final int[][] PST_DEFAULT = {
            { 0,  5, 10, 15, 10,  5,  0 }, // r0
            { 5, 10, 15, 20, 15, 10,  5 }, // r1
            { 10, 15, 20, 25, 20, 15, 10 }, // r2
            { 15, 20, 25, 30, 25, 20, 15 }, // r3
            { 20, 25, 30, 35, 30, 25, 20 }, // r4
            { 25, 30, 35, 40, 35, 30, 25 }, // r5
            { 30, 35, 40, 50, 40, 35, 30 }, // r6
            { 40, 50, 60, 75, 60, 50, 40 }, // r7
            { 50, 65, 80, 100, 80, 65, 50 }  // r8
    };

    // Rat PST (High bonus in rivers & riverbanks)
    private static final int[][] PST_RAT = {
            { 0,  5, 10, 15, 10,  5,  0 },
            { 5, 10, 15, 20, 15, 10,  5 },
            { 10, 20, 25, 25, 25, 20, 10 },
            { 15, 35, 40, 30, 40, 35, 15 },
            { 20, 40, 45, 35, 45, 40, 20 },
            { 25, 35, 40, 35, 40, 35, 25 },
            { 30, 40, 45, 55, 45, 40, 30 },
            { 40, 55, 65, 80, 65, 55, 40 },
            { 50, 70, 85, 100, 85, 70, 50 }
    };

    // Jumper PST (Lion & Tiger: High bonus on riverbank jumping squares)
    private static final int[][] PST_JUMPER = {
            { 0,  5, 10, 15, 10,  5,  0 },
            { 5, 10, 15, 20, 15, 10,  5 },
            { 15, 30, 35, 30, 35, 30, 15 }, // Riverbanks (r2)
            { 15, 20, 25, 30, 25, 20, 15 },
            { 20, 25, 30, 35, 30, 25, 20 },
            { 25, 30, 35, 40, 35, 30, 25 },
            { 20, 35, 40, 55, 40, 35, 20 }, // Riverbanks (r6)
            { 40, 50, 65, 80, 65, 50, 40 },
            { 50, 65, 85, 100, 85, 65, 50 }
    };

    /**
     * Evaluates the board from the perspective of the specified side.
     * Returns a positive score if 'side' is favored, negative if opponent is favored.
     */
    public int evaluate(Board board, Side side) {
        Side opponent = side.getOpposite();

        Position targetDen = (side == Side.PLAYER_1) ? new Position(8, 3) : new Position(0, 3);
        Position ownDen = (side == Side.PLAYER_1) ? new Position(0, 3) : new Position(8, 3);

        // Terminal check: piece in enemy den = Instant Win
        Piece targetDenPiece = board.getPiece(targetDen);
        if (targetDenPiece != null && targetDenPiece.side() == side) {
            return WIN_SCORE;
        }

        Piece ownDenPiece = board.getPiece(ownDen);
        if (ownDenPiece != null && ownDenPiece.side() == opponent) {
            return LOSS_SCORE;
        }

        int score = 0;
        int minOpponentDistToOwnDen = Integer.MAX_VALUE;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece == null)
                    continue;

                Side pieceSide = piece.side();
                Side pieceOpponent = pieceSide.getOpposite();

                int pieceVal = PIECE_VALUES.getOrDefault(piece.type(), 0);
                int positionalBonus = getPstValue(piece, r, c);

                // River bonus for Rat
                if (piece.type() == PieceType.RAT && Board.isRiver(r, c)) {
                    positionalBonus += 30;
                }

                // Trap penalty: piece standing in enemy trap is neutralized
                int trapPenalty = 0;
                if (Board.isTrap(r, c, pieceOpponent)) {
                    trapPenalty = pieceVal;
                }

                // Enemy Trap Control Bonus: reward pieces controlling squares adjacent to enemy traps
                int trapControlBonus = 0;
                if (isAdjacentToEnemyTrap(r, c, pieceOpponent)) {
                    trapControlBonus = 40;
                }

                // Rat vs Elephant interaction
                int nemesisPenalty = 0;
                if (piece.type() == PieceType.ELEPHANT) {
                    Position enemyRatPos = findPiecePosition(board, pieceOpponent, PieceType.RAT);
                    if (enemyRatPos != null) {
                        int distToRat = Math.abs(r - enemyRatPos.row()) + Math.abs(c - enemyRatPos.col());
                        if (distToRat == 1 && !Board.isRiver(enemyRatPos.row(), enemyRatPos.col())) {
                            nemesisPenalty = 800; // Immediate Rat threat on land: retreat!
                        } else if (distToRat == 2) {
                            nemesisPenalty = 300;
                        }
                    }
                }

                int totalPieceScore = pieceVal + positionalBonus + trapControlBonus - trapPenalty - nemesisPenalty;

                if (pieceSide == side) {
                    score += totalPieceScore;
                } else {
                    score -= totalPieceScore;
                    int distToOurDen = Math.abs(r - ownDen.row()) + Math.abs(c - ownDen.col());
                    if (distToOurDen < minOpponentDistToOwnDen) {
                        minOpponentDistToOwnDen = distToOurDen;
                    }
                }
            }
        }

        // Home Den & Trap Defense Evaluation
        score += evaluateHomeTrapAmbush(board, side);
        score -= evaluateHomeTrapAmbush(board, opponent);

        if (minOpponentDistToOwnDen <= 3) {
            boolean isDefended = isHomeDenDefended(board, side);
            if (minOpponentDistToOwnDen == 1) {
                score -= isDefended ? 500 : 2500;
            } else if (minOpponentDistToOwnDen == 2) {
                score -= isDefended ? 250 : 800;
            } else if (minOpponentDistToOwnDen == 3) {
                score -= isDefended ? 100 : 300;
            }
            if (isDefended) {
                score += 200;
            }
        }

        return score;
    }

    private int evaluateHomeTrapAmbush(Board board, Side side) {
        int bonus = 0;
        int[][] homeTraps = (side == Side.PLAYER_1)
                ? new int[][] { { 0, 2 }, { 0, 4 }, { 1, 3 } }
                : new int[][] { { 8, 2 }, { 8, 4 }, { 7, 3 } };

        for (int[] trap : homeTraps) {
            Piece trapPiece = board.getPiece(trap[0], trap[1]);

            if (trapPiece != null && trapPiece.side() == side) {
                // Clogging own trap: small penalty
                bonus -= 50;
            } else if (trapPiece != null && trapPiece.side() == side.getOpposite()) {
                // Enemy trapped inside friendly trap! Huge bonus if defender is adjacent ready to capture
                if (hasFriendlyPieceAdjacent(board, trap[0], trap[1], side)) {
                    bonus += 300;
                }
            } else {
                // Trap is empty — reward ambushing friendly pieces standing adjacent to trap
                if (hasFriendlyPieceAdjacent(board, trap[0], trap[1], side)) {
                    bonus += 150;
                }
            }
        }
        return bonus;
    }

    private boolean hasFriendlyPieceAdjacent(Board board, int row, int col, Side friendlySide) {
        int[][] directions = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        for (int[] dir : directions) {
            int r = row + dir[0];
            int c = col + dir[1];
            if (r >= 0 && r < Board.ROWS && c >= 0 && c < Board.COLS) {
                Piece p = board.getPiece(r, c);
                if (p != null && p.side() == friendlySide) {
                    return true;
                }
            }
        }
        return false;
    }

    private int getPstValue(Piece piece, int row, int col) {
        int r = (piece.side() == Side.PLAYER_1) ? row : (8 - row);
        if (piece.type() == PieceType.RAT) {
            return PST_RAT[r][col];
        } else if (piece.type() == PieceType.LION || piece.type() == PieceType.TIGER) {
            return PST_JUMPER[r][col];
        } else {
            return PST_DEFAULT[r][col];
        }
    }

    private boolean isAdjacentToEnemyTrap(int row, int col, Side pieceOpponent) {
        // Simple check if (row, col) is neighbor of any enemy trap
        int[][] enemyTraps = (pieceOpponent == Side.PLAYER_2)
                ? new int[][] { { 8, 2 }, { 8, 4 }, { 7, 3 } }
                : new int[][] { { 0, 2 }, { 0, 4 }, { 1, 3 } };

        for (int[] trap : enemyTraps) {
            if (Math.abs(row - trap[0]) + Math.abs(col - trap[1]) == 1) {
                return true;
            }
        }
        return false;
    }

    private boolean isHomeDenDefended(Board board, Side side) {
        int[][] defenderSquares = (side == Side.PLAYER_1)
                ? new int[][] { { 0, 2 }, { 0, 4 }, { 1, 3 }, { 1, 2 }, { 1, 4 }, { 2, 3 } }
                : new int[][] { { 8, 2 }, { 8, 4 }, { 7, 3 }, { 7, 2 }, { 7, 4 }, { 6, 3 } };

        for (int[] sq : defenderSquares) {
            Piece p = board.getPiece(sq[0], sq[1]);
            if (p != null && p.side() == side) {
                return true;
            }
        }
        return false;
    }

    private Position findPiecePosition(Board board, Side side, PieceType type) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.getPiece(r, c);
                if (p != null && p.side() == side && p.type() == type) {
                    return new Position(r, c);
                }
            }
        }
        return null;
    }
}
