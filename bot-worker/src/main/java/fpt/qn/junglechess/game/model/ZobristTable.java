package fpt.qn.junglechess.game.model;

import java.util.Random;

/**
 * Pre-computed Zobrist random table for 64-bit board hashing.
 * Indexed by [side (0=P1, 1=P2)][piece type ordinal][square = row*7+col].
 * Fixed seed so hashes are deterministic within the process lifetime.
 */
public final class ZobristTable {

    private static final long[][][] TABLE = new long[2][PieceType.values().length][Board.ROWS * Board.COLS];

    static {
        Random rng = new Random(0xDEADBEEFCAFEBABEL);
        for (int s = 0; s < 2; s++) {
            for (int p = 0; p < PieceType.values().length; p++) {
                for (int sq = 0; sq < Board.ROWS * Board.COLS; sq++) {
                    TABLE[s][p][sq] = rng.nextLong();
                }
            }
        }
    }

    private ZobristTable() {}

    /**
     * XOR this value in on makeMove and back out on undoMove for incremental hashing.
     */
    public static long forPiece(Piece piece, int row, int col) {
        int sideIdx = piece.side() == Side.PLAYER_1 ? 0 : 1;
        return TABLE[sideIdx][piece.type().ordinal()][row * Board.COLS + col];
    }
}
