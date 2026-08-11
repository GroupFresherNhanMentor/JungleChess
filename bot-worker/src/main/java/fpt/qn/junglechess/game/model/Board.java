package fpt.qn.junglechess.game.model;

import java.util.Arrays;

public class Board {
    public static final int ROWS = 9;
    public static final int COLS = 7;

    private final Piece[][] grid;
    /** Running Zobrist hash, updated incrementally on every makeMove/undoMove. */
    private long zobristHash;

    public Board() {
        this.grid = new Piece[ROWS][COLS];
        this.zobristHash = 0L;
    }

    public long getZobristHash() {
        return zobristHash;
    }

    public static Board createInitialBoard() {
        Board board = new Board();
        // Player 1 pieces
        board.setPiece(0, 0, new Piece(Side.PLAYER_1, PieceType.LION));
        board.setPiece(0, 6, new Piece(Side.PLAYER_1, PieceType.TIGER));
        board.setPiece(1, 1, new Piece(Side.PLAYER_1, PieceType.DOG));
        board.setPiece(1, 5, new Piece(Side.PLAYER_1, PieceType.CAT));
        board.setPiece(2, 0, new Piece(Side.PLAYER_1, PieceType.RAT));
        board.setPiece(2, 2, new Piece(Side.PLAYER_1, PieceType.LEOPARD));
        board.setPiece(2, 4, new Piece(Side.PLAYER_1, PieceType.WOLF));
        board.setPiece(2, 6, new Piece(Side.PLAYER_1, PieceType.ELEPHANT));

        // Player 2 pieces
        board.setPiece(8, 6, new Piece(Side.PLAYER_2, PieceType.LION));
        board.setPiece(8, 0, new Piece(Side.PLAYER_2, PieceType.TIGER));
        board.setPiece(7, 5, new Piece(Side.PLAYER_2, PieceType.DOG));
        board.setPiece(7, 1, new Piece(Side.PLAYER_2, PieceType.CAT));
        board.setPiece(6, 6, new Piece(Side.PLAYER_2, PieceType.RAT));
        board.setPiece(6, 4, new Piece(Side.PLAYER_2, PieceType.LEOPARD));
        board.setPiece(6, 2, new Piece(Side.PLAYER_2, PieceType.WOLF));
        board.setPiece(6, 0, new Piece(Side.PLAYER_2, PieceType.ELEPHANT));

        board.recomputeZobrist();
        return board;
    }

    /** Full Zobrist recompute from scratch. */
    public void recomputeZobrist() {
        zobristHash = 0L;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] != null) {
                    zobristHash ^= ZobristTable.forPiece(grid[r][c], r, c);
                }
            }
        }
    }

    public Piece getPiece(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return null;
        }
        return grid[row][col];
    }

    public Piece getPiece(Position pos) {
        return getPiece(pos.row(), pos.col());
    }

    public void setPiece(int row, int col, Piece piece) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            grid[row][col] = piece;
        }
    }

    public void setPiece(Position pos, Piece piece) {
        setPiece(pos.row(), pos.col(), piece);
    }

    // In-Place Make Move — XOR hash incrementally
    public void makeMove(Move move) {
        // Remove moved piece from source square
        zobristHash ^= ZobristTable.forPiece(move.movedPiece(), move.from().row(), move.from().col());
        // Remove captured piece from destination square (if any)
        if (move.capturedPiece() != null) {
            zobristHash ^= ZobristTable.forPiece(move.capturedPiece(), move.to().row(), move.to().col());
        }
        // Place moved piece on destination square
        zobristHash ^= ZobristTable.forPiece(move.movedPiece(), move.to().row(), move.to().col());

        setPiece(move.from(), null);
        setPiece(move.to(), move.movedPiece());
    }

    // In-Place Undo Move — reverses makeMove XOR operations exactly
    public void undoMove(Move move) {
        // Remove moved piece from destination square
        zobristHash ^= ZobristTable.forPiece(move.movedPiece(), move.to().row(), move.to().col());
        // Restore captured piece to destination square (if any)
        if (move.capturedPiece() != null) {
            zobristHash ^= ZobristTable.forPiece(move.capturedPiece(), move.to().row(), move.to().col());
        }
        // Restore moved piece to source square
        zobristHash ^= ZobristTable.forPiece(move.movedPiece(), move.from().row(), move.from().col());

        setPiece(move.from(), move.movedPiece());
        setPiece(move.to(), move.capturedPiece());
    }

    public Board cloneBoard() {
        Board clone = new Board();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                clone.grid[r][c] = this.grid[r][c];
            }
        }
        clone.zobristHash = this.zobristHash;
        return clone;
    }

    // Terrain helpers
    public static boolean isRiver(int row, int col) {
        return (row >= 3 && row <= 5) && ((col >= 1 && col <= 2) || (col >= 4 && col <= 5));
    }

    public static boolean isRiver(Position pos) {
        return isRiver(pos.row(), pos.col());
    }

    public static boolean isDen(int row, int col, Side ownerSide) {
        if (ownerSide == Side.PLAYER_1) {
            return row == 0 && col == 3;
        } else {
            return row == 8 && col == 3;
        }
    }

    public static boolean isDen(Position pos, Side ownerSide) {
        return isDen(pos.row(), pos.col(), ownerSide);
    }

    public static boolean isTrap(int row, int col, Side ownerSide) {
        if (ownerSide == Side.PLAYER_1) {
            return (row == 0 && col == 2) || (row == 0 && col == 4) || (row == 1 && col == 3);
        } else {
            return (row == 8 && col == 2) || (row == 8 && col == 4) || (row == 7 && col == 3);
        }
    }

    public static boolean isTrap(Position pos, Side ownerSide) {
        return isTrap(pos.row(), pos.col(), ownerSide);
    }

    public String[][] toBoardStateArray() {
        String[][] state = new String[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                state[r][c] = grid[r][c] != null ? grid[r][c].getPieceCode() : null;
            }
        }
        return state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Board board = (Board) o;
        return Arrays.deepEquals(grid, board.grid);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(grid);
    }
}
