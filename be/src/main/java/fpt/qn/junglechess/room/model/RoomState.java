package fpt.qn.junglechess.room.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomState {

    String roomId;
    GameMode mode;
    RoomStatus status;
    boolean allowSpectator;
    boolean allowBet;
    String[][] board;
    String currentTurn;
    int moveNumber;
    int botSearchDepth;
    @Builder.Default
    List<PlayerInfo> players = new ArrayList<>();
    @Builder.Default
    List<SpectatorInfo> spectators = new ArrayList<>();
    @Builder.Default
    List<MoveRecord> history = new ArrayList<>();
    Instant createdAt;
    Instant updatedAt;
}
