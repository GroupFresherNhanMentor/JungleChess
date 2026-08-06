package fpt.qn.junglechess.room.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MoveAckResponse {

    boolean accepted;

    public static MoveAckResponse ok() {
        return new MoveAckResponse(true);
    }
}
