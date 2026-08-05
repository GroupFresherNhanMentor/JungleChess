package fpt.qn.junglechess.common.dto;

import java.util.List;
import java.util.function.Function;

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
public class PageResponse<T> {
    long totalElements;
    int totalPages;
    int pageNumber;
    int pageSize;
    List<T> items;

    public static <T> PageResponse<T> of(List<T> items, int pageNumber, int pageSize, long totalElements) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
        return PageResponse.<T>builder()
                .items(items)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    public <U> PageResponse<U> map(Function<T, U> mapper) {
        return PageResponse.<U>builder()
                .items(items.stream().map(mapper).toList())
                .totalElements(totalElements)
                .totalPages(totalPages)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .build();
    }
}
