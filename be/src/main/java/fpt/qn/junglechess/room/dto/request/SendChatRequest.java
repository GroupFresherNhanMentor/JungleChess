package fpt.qn.junglechess.room.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SendChatRequest {

    @NotBlank(message = "Message content cannot be blank")
    @Size(max = 500, message = "Message content cannot exceed 500 characters")
    private String content;
}
