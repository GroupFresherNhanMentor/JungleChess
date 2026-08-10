package fpt.qn.junglechess.game.bot;

/**
 * Player-facing bot difficulty levels and their default minimax search depths.
 * Defaults mirror the {@code bots} table seed data (docs/DB_Design §3.3).
 */
public enum BotDifficulty {
    EASY(2),
    MEDIUM(4),
    HARD(6);

    private final int searchDepth;

    BotDifficulty(int searchDepth) {
        this.searchDepth = searchDepth;
    }

    public int getSearchDepth() {
        return searchDepth;
    }

    /**
     * Resolves a string to a {@link BotDifficulty}. Unknown or {@code null} input
     * defaults to {@link #MEDIUM} so room creation never fails on a bad value.
     */
    public static BotDifficulty from(String value) {
        if (value == null) {
            return MEDIUM;
        }
        try {
            return BotDifficulty.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
