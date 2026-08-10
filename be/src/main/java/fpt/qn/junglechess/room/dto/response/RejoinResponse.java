package fpt.qn.junglechess.room.dto.response;

import fpt.qn.junglechess.room.model.MoveRecord;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RejoinResponse {
    String yourSide;
    String status;
    String[][] board;
    String currentTurn;
    int moveNumber;
    List<MoveRecord> history;
}
