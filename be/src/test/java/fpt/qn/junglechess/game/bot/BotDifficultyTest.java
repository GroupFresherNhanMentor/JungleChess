package fpt.qn.junglechess.game.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotDifficultyTest {

    @Test
    @DisplayName("Each difficulty maps to its documented default search depth")
    void mapsToDocumentedDepths() {
        assertEquals(2, BotDifficulty.EASY.getSearchDepth());
        assertEquals(4, BotDifficulty.MEDIUM.getSearchDepth());
        assertEquals(6, BotDifficulty.HARD.getSearchDepth());
    }

    @Test
    @DisplayName("from(String) resolves case-insensitive difficulty names")
    void fromResolvesDifficultyNames() {
        assertEquals(BotDifficulty.EASY, BotDifficulty.from("EASY"));
        assertEquals(BotDifficulty.MEDIUM, BotDifficulty.from("medium"));
        assertEquals(BotDifficulty.HARD, BotDifficulty.from("Hard"));
    }

    @Test
    @DisplayName("from(String) defaults unknown or null input to MEDIUM")
    void fromDefaultsUnknownToMedium() {
        assertEquals(BotDifficulty.MEDIUM, BotDifficulty.from("EXTREME"));
        assertEquals(BotDifficulty.MEDIUM, BotDifficulty.from(""));
        assertEquals(BotDifficulty.MEDIUM, BotDifficulty.from(null));
    }
}
