package com.invokingmachines.multistorage.query.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResult {

    private List<Map<String, Object>> content;
    private Long totalElements;
    private Integer totalPages;
    private Integer size;
    private Integer number;

    public static SearchResult ofList(List<Map<String, Object>> content) {
        return new SearchResult(content, null, null, null, null);
    }

    public static SearchResult ofPage(List<Map<String, Object>> content, long totalElements, int size, int number) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new SearchResult(content, totalElements, totalPages, size, number);
    }

    public boolean hasPagination() {
        return totalElements != null;
    }
}
