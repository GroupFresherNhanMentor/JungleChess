package fpt.qn.junglechess.room.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LobbyRoomEntry {

    String roomId;
    String mode;
    String status;
    boolean allowSpectator;
    boolean allowBet;
    int playerCount;
    int spectatorCount;
    Instant createdAt;
}
