package fpt.qn.junglechess.room.dto.event;

import fpt.qn.junglechess.room.model.MoveRecord;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StateUpdatedEvent extends RoomEvent {

    String roomId;
    String[][] board;
    String currentTurn;
    MoveRecord lastMove;
    String status;
    int moveNumber;

    public StateUpdatedEvent(String roomId, String[][] board, String currentTurn,
                             MoveRecord lastMove, String status, int moveNumber) {
        super("STATE_UPDATED");
        this.roomId = roomId;
        this.board = board;
        this.currentTurn = currentTurn;
        this.lastMove = lastMove;
        this.status = status;
        this.moveNumber = moveNumber;
    }
}
