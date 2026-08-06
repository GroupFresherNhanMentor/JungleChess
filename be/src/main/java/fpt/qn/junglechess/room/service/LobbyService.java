package fpt.qn.junglechess.room.service;

import fpt.qn.junglechess.room.dto.response.LobbyRoomEntry;
import fpt.qn.junglechess.room.dto.response.LobbySnapshot;
import fpt.qn.junglechess.room.model.RoomState;
import fpt.qn.junglechess.room.model.RoomStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

@Service
public class LobbyService {

    private final Sinks.Many<LobbySnapshot> lobbySink =
            Sinks.many().replay().latest();

    public void emitSnapshot(List<RoomState> activeRooms) {
        List<LobbyRoomEntry> entries = activeRooms.stream()
                .filter(this::isVisible)
                .map(this::toEntry)
                .toList();
        lobbySink.tryEmitNext(LobbySnapshot.of(entries));
    }

    public Flux<LobbySnapshot> stream() {
        return lobbySink.asFlux();
    }

    private boolean isVisible(RoomState room) {
        if (room.getStatus() == RoomStatus.WAITING) return true;
        if (room.getStatus() == RoomStatus.PLAYING && room.isAllowSpectator()) return true;
        return false;
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
