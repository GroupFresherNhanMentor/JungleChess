package fpt.qn.junglechess.botworker.dto;

public record StateUpdatedEventDto(
        String roomId,
        String[][] board,
        String currentTurn,
        String status,
        int moveNumber
) {}
