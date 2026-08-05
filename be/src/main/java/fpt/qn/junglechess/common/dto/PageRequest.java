package fpt.qn.junglechess.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageRequest {

    @Min(0)
     int page = 0;

    @Min(1)
    @Max(100)
     int size = 20;
}
