package fpt.qn.junglechess.room.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateRoomResponse {

    String roomId;
    String mode;
    String status;
    String yourSide;
    boolean allowSpectator;
    boolean allowBet;
}
