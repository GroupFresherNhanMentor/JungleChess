package fpt.qn.junglechess.game.model;

public record Position(int row, int col) {
    public boolean isValid() {
        return isValid(row, col);
    }

    public static boolean isValid(int row, int col) {
        return row >= 0 && row < Board.ROWS && col >= 0 && col < Board.COLS;
    }
}
