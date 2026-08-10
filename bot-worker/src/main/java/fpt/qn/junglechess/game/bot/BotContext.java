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
    private final int[][] historyTable = new int[63][63];

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

    public void recordHistory(int fromSq, int toSq, int depth) {
        if (fromSq >= 0 && fromSq < 63 && toSq >= 0 && toSq < 63) {
            historyTable[fromSq][toSq] += depth * depth;
        }
    }

    public int getHistoryScore(int fromSq, int toSq) {
        if (fromSq >= 0 && fromSq < 63 && toSq >= 0 && toSq < 63) {
            return historyTable[fromSq][toSq];
        }
        return 0;
    }

    public void decayHistory() {
        for (int r = 0; r < 63; r++) {
            for (int c = 0; c < 63; c++) {
                historyTable[r][c] >>= 1;
            }
        }
    }

    public void clear() {
        transpositionTable.clear();
        positionHistory.clear();
        for (int r = 0; r < 63; r++) {
            java.util.Arrays.fill(historyTable[r], 0);
        }
    }
}
