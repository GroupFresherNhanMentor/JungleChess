package fpt.qn.junglechess.game.rule;

import fpt.qn.junglechess.game.model.*;

import java.util.ArrayList;
import java.util.List;

public class DefaultGameRuleEngine implements GameRuleEngine {

    private static final int[][] DIRECTIONS = {
        {-1, 0}, // Up
        {1, 0},  // Down
        {0, -1}, // Left
        {0, 1}   // Right
    };

    @Override
    public List<Move> getValidMoves(Board board, Side side) {
        List<Move> validMoves = new ArrayList<>();

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece piece = board.getPiece(r, c);
                if (piece != null && piece.side() == side) {
                    Position from = new Position(r, c);
                    generateMovesForPiece(board, from, piece, validMoves);
                }
            }
        }

        return validMoves;
    }

    @Override
    public boolean isValidMove(Board board, Move move) {
        if (move == null || move.from() == null || move.to() == null) {
            return false;
        }

        Piece movedPiece = board.getPiece(move.from());
        if (movedPiece == null || movedPiece.side() != move.movedPiece().side() || movedPiece.type() != move.movedPiece().type()) {
            return false;
        }

        List<Move> validMoves = getValidMoves(board, movedPiece.side());
        for (Move validMove : validMoves) {
            if (validMove.from().equals(move.from()) && validMove.to().equals(move.to())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isGameOver(Board board) {
        return getWinner(board) != null;
    }

    @Override
    public Side getWinner(Board board) {
        // Check if any P1 piece reached P2 Den [8, 3]
        Piece p1InP2Den = board.getPiece(8, 3);
        if (p1InP2Den != null && p1InP2Den.side() == Side.PLAYER_1) {
            return Side.PLAYER_1;
        }

        // Check if any P2 piece reached P1 Den [0, 3]
        Piece p2InP1Den = board.getPiece(0, 3);
        if (p2InP1Den != null && p2InP1Den.side() == Side.PLAYER_2) {
            return Side.PLAYER_2;
        }

        // Check if a side has no valid moves or no pieces left
        boolean p1HasMoves = !getValidMoves(board, Side.PLAYER_1).isEmpty();
        boolean p2HasMoves = !getValidMoves(board, Side.PLAYER_2).isEmpty();

        if (!p1HasMoves) {
            return Side.PLAYER_2;
        }
        if (!p2HasMoves) {
            return Side.PLAYER_1;
        }

        return null;
    }

    private void generateMovesForPiece(Board board, Position from, Piece piece, List<Move> validMoves) {
        for (int[] dir : DIRECTIONS) {
            int targetRow = from.row() + dir[0];
            int targetCol = from.col() + dir[1];

            Position nextPos = new Position(targetRow, targetCol);
            if (!nextPos.isValid()) {
                continue;
            }

            // Den restriction: cannot enter own Den
            if (Board.isDen(nextPos, piece.side())) {
                continue;
            }

            // Check River Jump for Tiger and Lion
            if (Board.isRiver(nextPos)) {
                if (piece.type() == PieceType.TIGER || piece.type() == PieceType.LION) {
                    handleRiverJump(board, from, piece, dir, validMoves);
                    continue;
                } else if (piece.type() != PieceType.RAT) {
                    // Only Rat, Tiger, Lion can interact with river
                    continue;
                }
            }

            // Standard step to nextPos
            checkAndAddMove(board, from, nextPos, piece, validMoves, null);
        }
    }

    private void handleRiverJump(Board board, Position from, Piece piece, int[] dir, List<Move> validMoves) {
        int r = from.row() + dir[0];
        int c = from.col() + dir[1];
        boolean blockedByRat = false;

        while (Position.isValid(r, c) && Board.isRiver(r, c)) {
            Piece riverPiece = board.getPiece(r, c);
            if (riverPiece != null && riverPiece.type() == PieceType.RAT) {
                blockedByRat = true;
                break;
            }
            r += dir[0];
            c += dir[1];
        }

        if (!blockedByRat && Position.isValid(r, c)) {
            Position landPos = new Position(r, c);
            if (!Board.isDen(landPos, piece.side())) {
                checkAndAddMove(board, from, landPos, piece, validMoves, SpecialEvent.RIVER_JUMP);
            }
        }
    }

    private void checkAndAddMove(Board board, Position from, Position to, Piece movedPiece, List<Move> validMoves, SpecialEvent forcedEvent) {
        Piece targetPiece = board.getPiece(to);
        Side friendlySide = movedPiece.side();

        if (targetPiece == null) {
            // Free square
            validMoves.add(new Move(from, to, movedPiece, null, forcedEvent));
            return;
        }

        if (targetPiece.side() == friendlySide) {
            // Cannot capture own piece
            return;
        }

        // Target piece is enemy piece
        SpecialEvent event = forcedEvent;

        // Check Trap neutralization rule: Enemy in friendly trap drops rank to 0
        boolean isEnemyInFriendlyTrap = Board.isTrap(to, friendlySide);
        if (isEnemyInFriendlyTrap) {
            event = SpecialEvent.TRAP_NEUTRALIZED;
            validMoves.add(new Move(from, to, movedPiece, targetPiece, event));
            return;
        }

        // River boundary rules for Rat & Elephant
        boolean attackerInRiver = Board.isRiver(from);
        boolean targetInRiver = Board.isRiver(to);

        if (attackerInRiver && !targetInRiver) {
            // Rat in river cannot capture piece on land
            return;
        }

        // Capture Rank Check
        if (canCapture(movedPiece, targetPiece)) {
            validMoves.add(new Move(from, to, movedPiece, targetPiece, event));
        }
    }

    private boolean canCapture(Piece attacker, Piece defender) {
        // Exception: Rat vs Elephant
        if (attacker.type() == PieceType.RAT && defender.type() == PieceType.ELEPHANT) {
            return true;
        }
        if (attacker.type() == PieceType.ELEPHANT && defender.type() == PieceType.RAT) {
            return false;
        }

        // Standard Rank Check
        return attacker.getRank() >= defender.getRank();
    }
}
