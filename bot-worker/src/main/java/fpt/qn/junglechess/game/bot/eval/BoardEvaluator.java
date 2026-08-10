package fpt.qn.junglechess.game.bot.eval;

import fpt.qn.junglechess.game.model.Board;
import fpt.qn.junglechess.game.model.Piece;
import fpt.qn.junglechess.game.model.PieceType;
import fpt.qn.junglechess.game.model.Position;
import fpt.qn.junglechess.game.model.Side;
import fpt.qn.junglechess.game.rule.GameRuleEngine;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class BoardEvaluator {

    public static final int WIN_SCORE = 100000;
    public static final int LOSS_SCORE = -100000;

    private final GameRuleEngine gameRuleEngine;

    public BoardEvaluator(GameRuleEngine gameRuleEngine) {
        this.gameRuleEngine = gameRuleEngine;
    }

    private static final Map<PieceType, Integer> PIECE_VALUES = new EnumMap<>(PieceType.class);

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

    /**
     * Evaluates the board position from the perspective of side.
     * Positive score means advantage for side, negative means disadvantage.
     */
    public int evaluate(Board board, Side side) {
        Side opponent = side.getOpposite();

        // Check terminal conditions (entering den)
        Position targetDen = (side == Side.PLAYER_1) ? new Position(8, 3) : new Position(0, 3);
        Position ownDen = (side == Side.PLAYER_1) ? new Position(0, 3) : new Position(8, 3);

        Piece targetDenPiece = board.getPiece(targetDen);
        if (targetDenPiece != null && targetDenPiece.side() == side) {
            return WIN_SCORE;
        }

        Piece ownDenPiece = board.getPiece(ownDen);
        if (ownDenPiece != null && ownDenPiece.side() == opponent) {
            return LOSS_SCORE;
        }

        int score = 0;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece == null) continue;

                int pieceVal = PIECE_VALUES.getOrDefault(piece.type(), 0);

                int enemyDenRow = (piece.side() == Side.PLAYER_1) ? 8 : 0;
                int enemyDenCol = 3;
                int distance = Math.abs(r - enemyDenRow) + Math.abs(c - enemyDenCol);

                // Role-aware positional scoring
                int positionalBonus = calculateRolePositionalBonus(board, piece, r, c, distance);

                boolean threatened = isThreatened(board, r, c, piece, piece.side().getOpposite());

                // Gated near-den bonus: only reward if 1 step away AND NOT threatened
                if (distance == 1 && !threatened) {
                    positionalBonus += 250;
                }

                // Trap penalty if standing in enemy trap
                int trapPenalty = 0;
                if (Board.isTrap(r, c, piece.side().getOpposite())) {
                    trapPenalty = 300;
                }

                // Trap-luring bonus: enemy piece near our trap while we guard it
                int trapLuringBonus = 0;
                if (piece.side() != side && Board.isTrap(r, c, side)) {
                    trapLuringBonus = 120; // Enemy stepped into our trap!
                }

                // SEE Threat weight: evaluates whether defending piece makes trade unfavorable for attacker
                int threatWeight = 0;
                if (threatened) {
                    threatWeight = calculateSeeThreatWeight(board, r, c, piece, piece.side().getOpposite());
                }

                int totalPieceScore = pieceVal + positionalBonus + trapLuringBonus - trapPenalty - threatWeight;

                if (piece.side() == side) {
                    score += totalPieceScore;
                } else {
                    score -= totalPieceScore;
                }
            }
        }

        return score;
    }

    private int calculateSeeThreatWeight(Board board, int r, int c, Piece targetPiece, Side attackerSide) {
        int pieceVal = PIECE_VALUES.getOrDefault(targetPiece.type(), 0);
        boolean isDefended = isThreatened(board, r, c, targetPiece, targetPiece.side());

        if (isDefended) {
            // Find the lowest-value friendly defender that can recapture after an exchange
            Piece defender = findLowestValueDefender(board, r, c, targetPiece, targetPiece.side());
            Piece attacker = findStrongestAttacker(board, r, c, targetPiece, attackerSide);

            if (attacker != null && defender != null) {
                int attackerVal  = PIECE_VALUES.getOrDefault(attacker.type(), 0);
                int defenderVal  = PIECE_VALUES.getOrDefault(defender.type(), 0);
                // If the recapturing defender is cheaper than the attacker,
                // the attacker loses material on the exchange — trade is unfavorable for them.
                if (defenderVal < attackerVal) {
                    return 0; // Attacker won't make a losing trade
                }
            }
        }

        return (int) (pieceVal * 0.85);
    }

    private Piece findLowestValueDefender(Board board, int row, int col, Piece targetPiece, Side defenderSide) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        Piece cheapest = null;
        int minVal = Integer.MAX_VALUE;

        for (int[] dir : directions) {
            int er = row + dir[0];
            int ec = col + dir[1];
            if (!Position.isValid(er, ec)) continue;

            Piece defender = board.getPiece(er, ec);
            if (defender != null && defender.side() == defenderSide && !defender.equals(targetPiece)) {
                int val = PIECE_VALUES.getOrDefault(defender.type(), 0);
                if (val < minVal) {
                    minVal = val;
                    cheapest = defender;
                }
            }
        }
        return cheapest;
    }

    private Piece findStrongestAttacker(Board board, int row, int col, Piece piece, Side attackerSide) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        Piece strongest = null;
        int maxVal = -1;

        for (int[] dir : directions) {
            int er = row + dir[0];
            int ec = col + dir[1];
            if (!Position.isValid(er, ec)) continue;

            Piece attacker = board.getPiece(er, ec);
            if (attacker != null && attacker.side() == attackerSide) {
                if (gameRuleEngine.canCapture(attacker, piece)) {
                    int val = PIECE_VALUES.getOrDefault(attacker.type(), 0);
                    if (val > maxVal) {
                        maxVal = val;
                        strongest = attacker;
                    }
                }
            }
        }

        return strongest;
    }

    private int calculateRolePositionalBonus(Board board, Piece piece, int r, int c, int distanceToDen) {
        int baseMult = 4;
        int bonus = 0;

        switch (piece.type()) {
            case RAT -> {
                baseMult = 3;
                if (Board.isRiver(r, c)) {
                    bonus += 50; // River control
                    // Hunt/pin enemy Elephant
                    Position enemyElephantPos = findPiecePosition(board, piece.side().getOpposite(), PieceType.ELEPHANT);
                    if (enemyElephantPos != null) {
                        int distToEle = Math.abs(r - enemyElephantPos.row()) + Math.abs(c - enemyElephantPos.col());
                        if (distToEle <= 2) {
                            bonus += 80;
                        }
                    }
                }
            }
            case LION, TIGER -> {
                baseMult = 6;
                // Reward advancing past the river
                boolean crossed = (piece.side() == Side.PLAYER_1) ? r >= 6 : r <= 2;
                if (crossed) {
                    bonus += 60;
                }
            }
            case ELEPHANT -> {
                baseMult = 2; // Elephant advances carefully as a defender/blocker
            }
            default -> baseMult = 4;
        }

        return (14 - distanceToDen) * baseMult + bonus;
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

    private boolean isThreatened(Board board, int row, int col, Piece piece, Side attackerSide) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        boolean targetInRiver = Board.isRiver(row, col);
        boolean targetInEnemyTrap = Board.isTrap(row, col, attackerSide);

        // 1. Direct adjacent checks
        for (int[] dir : directions) {
            int er = row + dir[0];
            int ec = col + dir[1];
            if (er < 0 || er >= Board.ROWS || ec < 0 || ec >= Board.COLS) {
                continue;
            }

            Piece attacker = board.getPiece(er, ec);
            if (attacker == null || attacker.side() != attackerSide) {
                continue;
            }

            if (Board.isTrap(er, ec, piece.side())) {
                continue;
            }

            boolean attackerInRiver = Board.isRiver(er, ec);
            if (attackerInRiver != targetInRiver) {
                continue;
            }

            // Target in attacker's trap drops rank to 0, so any attacker captures it
            if (targetInEnemyTrap || gameRuleEngine.canCapture(attacker, piece)) {
                return true;
            }
        }

        // 2. River jump threat checks for Tiger & Lion (only applicable when target is on land)
        if (!targetInRiver) {
            for (int[] dir : directions) {
                int jr = row + dir[0];
                int jc = col + dir[1];

                if (!Position.isValid(jr, jc) || !Board.isRiver(jr, jc)) {
                    continue;
                }

                boolean blockedByRat = false;
                while (Position.isValid(jr, jc) && Board.isRiver(jr, jc)) {
                    Piece riverPiece = board.getPiece(jr, jc);
                    if (riverPiece != null && riverPiece.type() == PieceType.RAT) {
                        blockedByRat = true;
                        break;
                    }
                    jr += dir[0];
                    jc += dir[1];
                }

                if (!blockedByRat && Position.isValid(jr, jc)) {
                    Piece attacker = board.getPiece(jr, jc);
                    if (attacker != null && attacker.side() == attackerSide) {
                        if (attacker.type() == PieceType.TIGER || attacker.type() == PieceType.LION) {
                            if (!Board.isTrap(jr, jc, piece.side()) && (targetInEnemyTrap || gameRuleEngine.canCapture(attacker, piece))) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }
}
