package fpt.qn.junglechess.game.bot;

import fpt.qn.junglechess.game.model.Move;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-game state context owned by a single BotGameSession instance.
 * Houses the per-game Transposition Table and position history for repetition detection.
 */
public class BotContext {

    public record TranspositionEntry(int depth, int score, int flag, Move bestMove) {}

    private final Map<Long, TranspositionEntry> transpositionTable = new HashMap<>();
    private final Map<Long, Integer> positionHistory = new HashMap<>();

    public Map<Long, TranspositionEntry> getTranspositionTable() {
        return transpositionTable;
    }

    public Map<Long, Integer> getPositionHistory() {
        return positionHistory;
    }

    public void recordPosition(long hash) {
        positionHistory.put(hash, positionHistory.getOrDefault(hash, 0) + 1);
    }

    public int getPositionCount(long hash) {
        return positionHistory.getOrDefault(hash, 0);
    }

    public void clear() {
        transpositionTable.clear();
        positionHistory.clear();
    }
}
