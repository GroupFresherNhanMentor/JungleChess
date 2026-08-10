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

    // -------------------------------------------------------------------------
    // Piece-Square Tables (PST) for 9x7 Jungle Chess board (PLAYER_1 perspective)
    // Rows 0..8 (0=P1 home den row, 8=P2 home den row), Cols 0..6
    // -------------------------------------------------------------------------

    // Rat: High value in river (r=3..5, c=1,2,4,5) and advancing toward enemy Elephant
    private static final int[][] RAT_PST = {
        {  0,   0,   0,   0,   0,   0,   0}, // r0 (P1 Den)
        { 10,  15,  20,  15,  20,  15,  10}, // r1
        { 20,  25,  30,  25,  30,  25,  20}, // r2
        { 30,  70,  70,  35,  70,  70,  30}, // r3 (River)
        { 35,  75,  75,  40,  75,  75,  35}, // r4 (River)
        { 40,  70,  70,  45,  70,  70,  40}, // r5 (River)
        { 45,  50,  55,  50,  55,  50,  45}, // r6
        { 50,  60,  65,  70,  65,  60,  50}, // r7 (Enemy Trap area)
        { 55,  65,  75, 100,  75,  65,  55}  // r8 (Enemy Den area)
    };

    // Tiger & Lion: High value at riverbank launchpads (r=2,6 c=0,3,6) and deep penetration
    private static final int[][] LION_TIGER_PST = {
        {-20, -10,  -5, -10,  -5, -10, -20}, // r0
        {-10,   5,  10,   5,  10,   5, -10}, // r1
        { 50,  20,  25,  60,  25,  20,  50}, // r2 (Launchpads r2,c0 / r2,c3 / r2,c6)
        { 15, -50, -50,  20, -50, -50,  15}, // r3 (Flanks)
        { 20, -50, -50,  30, -50, -50,  20}, // r4
        { 25, -50, -50,  35, -50, -50,  25}, // r5
        { 60,  40,  45,  70,  45,  40,  60}, // r6 (Enemy Launchpads)
        { 50,  70,  80,  90,  80,  70,  50}, // r7
        { 60,  80,  95, 120,  95,  80,  60}  // r8 (Enemy Den area)
    };

    // Elephant: Prefers solid control, advances carefully, avoids enemy Rat river traps
    private static final int[][] ELEPHANT_PST = {
        { 10,  15,  20,  15,  20,  15,  10}, // r0
        { 15,  20,  25,  20,  25,  20,  15}, // r1
        { 20,  25,  30,  25,  30,  25,  20}, // r2
        { 15, -30, -30,  25, -30, -30,  15}, // r3
        { 20, -30, -30,  30, -30, -30,  20}, // r4
        { 25, -30, -30,  35, -30, -30,  25}, // r5
        { 30,  35,  40,  45,  40,  35,  30}, // r6
        { 35,  45,  55,  70,  55,  45,  35}, // r7
        { 40,  55,  70,  90,  70,  55,  40}  // r8
    };

    // Medium pieces (Dog, Cat, Wolf, Leopard): Flexible midcourt & den guards
    private static final int[][] GENERIC_PST = {
        {  0,   5,  10,  15,  10,   5,   0}, // r0
        {  5,  10,  15,  20,  15,  10,   5}, // r1
        { 10,  15,  20,  25,  20,  15,  10}, // r2
        { 15,  20,  25,  30,  25,  20,  15}, // r3
        { 20,  25,  30,  35,  30,  25,  20}, // r4
        { 25,  30,  35,  40,  35,  30,  25}, // r5
        { 30,  35,  40,  50,  40,  35,  30}, // r6
        { 40,  50,  60,  75,  60,  50,  40}, // r7
        { 50,  65,  80, 100,  80,  65,  50}  // r8
    };

    public int evaluate(Board board, Side side) {
        Side opponent = side.getOpposite();

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
        int minOpponentDistToOwnDen = Integer.MAX_VALUE;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece == null) continue;

                int pieceVal = PIECE_VALUES.getOrDefault(piece.type(), 0);
                int positionalBonus = getPstValue(piece, r, c);

                boolean threatened = isThreatened(board, r, c, piece, piece.side().getOpposite());

                int enemyDenRow = (piece.side() == Side.PLAYER_1) ? 8 : 0;
                int distanceToEnemyDen = Math.abs(r - enemyDenRow) + Math.abs(c - 3);

                // Gated near-den bonus: only reward if 1 step away AND NOT threatened
                if (distanceToEnemyDen == 1 && !threatened) {
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
                    trapLuringBonus = 120;
                }

                // SEE Threat weight: evaluates whether defending piece makes trade unfavorable for attacker
                int threatWeight = 0;
                if (threatened) {
                    threatWeight = calculateSeeThreatWeight(board, r, c, piece, piece.side().getOpposite());
                }

                // Nemesis Proximity: Elephant vs Enemy Rat urgency fleeing penalty
                int nemesisPenalty = 0;
                if (piece.type() == PieceType.ELEPHANT) {
                    Position enemyRatPos = findPiecePosition(board, piece.side().getOpposite(), PieceType.RAT);
                    if (enemyRatPos != null) {
                        int distToRat = Math.abs(r - enemyRatPos.row()) + Math.abs(c - enemyRatPos.col());
                        if (distToRat == 2) {
                            nemesisPenalty = 350; // Approaching Rat: retreat!
                        } else if (distToRat == 3) {
                            nemesisPenalty = 150; // Nearby Rat warning
                        }
                    }
                }

                int totalPieceScore = pieceVal + positionalBonus + trapLuringBonus - trapPenalty - threatWeight - nemesisPenalty;

                if (piece.side() == side) {
                    score += totalPieceScore;
                } else {
                    score -= totalPieceScore;
                    // Track closest enemy piece to our own den
                    int distToOurDen = Math.abs(r - ownDen.row()) + Math.abs(c - ownDen.col());
                    if (distToOurDen < minOpponentDistToOwnDen) {
                        minOpponentDistToOwnDen = distToOurDen;
                    }
                }
            }
        }

        // Den Path Defense: Penalize when an opponent piece is within 3 steps of our den
        if (minOpponentDistToOwnDen <= 3) {
            score -= (4 - minOpponentDistToOwnDen) * 180;
        }

        return score;
    }

    private int getPstValue(Piece piece, int r, int c) {
        // Evaluate from P1 perspective (flip row for P2)
        int row = (piece.side() == Side.PLAYER_1) ? r : (8 - r);
        int col = c;

        int[][] pst = switch (piece.type()) {
            case RAT -> RAT_PST;
            case LION, TIGER -> LION_TIGER_PST;
            case ELEPHANT -> ELEPHANT_PST;
            default -> GENERIC_PST;
        };

        return pst[row][col];
    }

    private int calculateSeeThreatWeight(Board board, int r, int c, Piece targetPiece, Side attackerSide) {
        int pieceVal = PIECE_VALUES.getOrDefault(targetPiece.type(), 0);
        boolean isDefended = isThreatened(board, r, c, targetPiece, targetPiece.side());

        if (isDefended) {
            Piece defender = findLowestValueDefender(board, r, c, targetPiece, targetPiece.side());
            Piece attacker = findStrongestAttacker(board, r, c, targetPiece, attackerSide);

            if (attacker != null && defender != null) {
                int attackerVal  = PIECE_VALUES.getOrDefault(attacker.type(), 0);
                int defenderVal  = PIECE_VALUES.getOrDefault(defender.type(), 0);

                // Asymmetric trade: If target is worth more than attacker (e.g. Elephant 800 vs Rat 100),
                // attacker will trade regardless of defender!
                if (pieceVal > attackerVal) {
                    return (int) (pieceVal * 0.95);
                }

                if (defenderVal < attackerVal) {
                    return 0; // Attacker won't make a losing trade
                }
            }
        }

        return (int) (pieceVal * 0.85);
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

    private boolean isThreatened(Board board, int row, int col, Piece piece, Side attackerSide) {
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        boolean targetInRiver = Board.isRiver(row, col);
        boolean targetInEnemyTrap = Board.isTrap(row, col, attackerSide);

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

            if (targetInEnemyTrap || gameRuleEngine.canCapture(attacker, piece)) {
                return true;
            }
        }

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
