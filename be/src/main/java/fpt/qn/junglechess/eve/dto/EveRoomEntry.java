package fpt.qn.junglechess.eve.dto;

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
public class EveRoomEntry {

    String roomId;
    String status;
    int moveNumber;
    int spectatorCount;
    BotContainerInfo bot1;
    BotContainerInfo bot2;
    Instant startedAt;
}
