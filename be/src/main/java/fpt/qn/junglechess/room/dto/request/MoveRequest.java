package fpt.qn.junglechess.room.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MoveRequest {

    @NotNull
    @Size(min = 2, max = 2)
    int[] from;

    @NotNull
    @Size(min = 2, max = 2)
    int[] to;
}
