package fpt.qn.junglechess.room.service;

import fpt.qn.junglechess.room.dto.response.LobbyRoomEntry;
import fpt.qn.junglechess.room.dto.response.LobbySnapshot;
import fpt.qn.junglechess.room.model.RoomState;
import fpt.qn.junglechess.room.model.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LobbyService {

    private final SimpMessagingTemplate messaging;

    private volatile LobbySnapshot latestSnapshot = LobbySnapshot.of(List.of());

    public void emitSnapshot(List<RoomState> activeRooms) {
        List<LobbyRoomEntry> entries = activeRooms.stream()
                .filter(this::isVisible)
                .map(this::toEntry)
                .toList();
        latestSnapshot = LobbySnapshot.of(entries);
        messaging.convertAndSend("/topic/lobby", latestSnapshot);
    }

    public LobbySnapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    private boolean isVisible(RoomState room) {
        if (room.getStatus() == RoomStatus.WAITING) return true;
        return room.getStatus() == RoomStatus.PLAYING && room.isAllowSpectator();
    }

    private LobbyRoomEntry toEntry(RoomState room) {
        return LobbyRoomEntry.builder()
                .roomId(room.getRoomId())
                .mode(room.getMode().name())
                .status(room.getStatus().name())
                .allowSpectator(room.isAllowSpectator())
                .allowBet(room.isAllowBet())
                .playerCount(room.getPlayers().size())
                .spectatorCount(room.getSpectators().size())
                .createdAt(room.getCreatedAt())
                .build();
    }
}
