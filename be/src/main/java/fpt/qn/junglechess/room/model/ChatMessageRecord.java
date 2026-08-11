package fpt.qn.junglechess.room.model;

public record ChatMessageRecord(
        String id,
        String senderUserId,
        String senderName,
        String senderSide,
        String content,
        long timestamp
) {}
