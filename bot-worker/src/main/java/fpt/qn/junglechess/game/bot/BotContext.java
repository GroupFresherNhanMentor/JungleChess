package fpt.qn.junglechess.game.bot;

import fpt.qn.junglechess.game.bot.tt.TtEntry;
import fpt.qn.junglechess.game.model.Move;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Per-game state context owned by a single BotGameSession instance.
 * Holds position history for repetition detection, Transposition Table (TT),
 * Killer Moves, and History Heuristic table.
 */
public class BotContext {

    public static final int MAX_PLY = 64;

    private final Map<Long, Integer> positionHistory = new HashMap<>();
    private final Map<Long, TtEntry> transpositionTable = new HashMap<>();
    private final Move[][] killerMoves = new Move[MAX_PLY][2];
    private final int[][] historyTable = new int[63][63];

    private int moveNumber = 0;
    private final long rngSeed = System.currentTimeMillis();
    private final Random openingRng = new Random(rngSeed);

    public int getMoveNumber() {
        return moveNumber;
    }

    public void incrementMoveNumber() {
        moveNumber++;
    }

    public Random getOpeningRng() {
        return openingRng;
    }

    public long getRngSeed() {
        return rngSeed;
    }

    public Map<Long, Integer> getPositionHistory() {
        return positionHistory;
    }

    public void recordPosition(long hash) {
        positionHistory.put(hash, positionHistory.getOrDefault(hash, 0) + 1);
    }

    public void pushPosition(long hash) {
        positionHistory.put(hash, positionHistory.getOrDefault(hash, 0) + 1);
    }

    public void popPosition(long hash) {
        int count = positionHistory.getOrDefault(hash, 0);
        if (count <= 1) {
            positionHistory.remove(hash);
        } else {
            positionHistory.put(hash, count - 1);
        }
    }

    public int getPositionCount(long hash) {
        return positionHistory.getOrDefault(hash, 0);
    }

    // Transposition Table Methods
    public TtEntry getTtEntry(long key) {
        return transpositionTable.get(key);
    }

    public void storeTtEntry(long key, TtEntry entry) {
        if (transpositionTable.size() > 200_000) {
            transpositionTable.entrySet().removeIf(e -> e.getValue().getDepth() <= 2);
            if (transpositionTable.size() > 200_000) {
                transpositionTable.clear();
            }
        }
        TtEntry existing = transpositionTable.get(key);
        if (existing == null || entry.getDepth() >= existing.getDepth()) {
            transpositionTable.put(key, entry);
        }
    }

    public int getTtSize() {
        return transpositionTable.size();
    }

    // Killer Moves Methods
    public Move[] getKillerMoves(int ply) {
        if (ply >= 0 && ply < MAX_PLY) {
            return killerMoves[ply];
        }
        return new Move[2];
    }

    public void storeKillerMove(int ply, Move move) {
        if (ply >= 0 && ply < MAX_PLY && move.capturedPiece() == null) {
            if (!move.equals(killerMoves[ply][0]) && !move.equals(killerMoves[ply][1])) {
                killerMoves[ply][1] = killerMoves[ply][0];
                killerMoves[ply][0] = move;
            }
        }
    }

    // History Heuristic Methods
    public void ageHistoryScores() {
        for (int i = 0; i < 63; i++) {
            for (int j = 0; j < 63; j++) {
                historyTable[i][j] /= 2;
            }
        }
    }

    public void addHistoryScore(Move move, int depth) {
        if (move.capturedPiece() == null) {
            int fromIdx = move.from().row() * 7 + move.from().col();
            int toIdx = move.to().row() * 7 + move.to().col();
            if (fromIdx >= 0 && fromIdx < 63 && toIdx >= 0 && toIdx < 63) {
                historyTable[fromIdx][toIdx] += depth * depth;
            }
        }
    }

    public int getHistoryScore(Move move) {
        int fromIdx = move.from().row() * 7 + move.from().col();
        int toIdx = move.to().row() * 7 + move.to().col();
        if (fromIdx >= 0 && fromIdx < 63 && toIdx >= 0 && toIdx < 63) {
            return historyTable[fromIdx][toIdx];
        }
        return 0;
    }

    public void clear() {
        moveNumber = 0;
        positionHistory.clear();
        transpositionTable.clear();
        for (int i = 0; i < MAX_PLY; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }
        for (int i = 0; i < 63; i++) {
            Arrays.fill(historyTable[i], 0);
        }
    }
}
