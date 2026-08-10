package fpt.qn.junglechess.room.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BotJoinRequest {
    private String side;
    private String difficulty;
}
