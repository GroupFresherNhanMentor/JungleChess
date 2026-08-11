package fpt.qn.junglechess.room.dto.event;

import fpt.qn.junglechess.room.model.ChatMessageRecord;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageEvent extends RoomEvent {

    String roomId;
    ChatMessageRecord message;

    public ChatMessageEvent(String roomId, ChatMessageRecord message) {
        super("CHAT_MESSAGE");
        this.roomId = roomId;
        this.message = message;
    }
}
