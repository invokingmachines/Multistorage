package com.invokingmachines.multistorage.dto.query;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
public class Query {

    private List<List<String>> select;
    private Criteria where = new Criteria(Logician.AND, Collections.emptyList());
    private Integer page;
    private Integer size;
    private Sort sort;

    @Data
    @NoArgsConstructor
    public static class Sort {
        private String by;
        private String order;

        public Sort(String by, String order) {
            this.by = by;
            this.order = order;
        }
    }

    public Query(List<List<String>> select, Criteria where) {
        this.select = select;
        this.where = where;
    }

    public Query(List<List<String>> select, Criteria where, Integer page, Integer size) {
        this.select = select;
        this.where = where;
        this.page = page;
        this.size = size;
    }

    public Query(List<List<String>> select, Criteria where, Integer page, Integer size, Sort sort) {
        this.select = select;
        this.where = where;
        this.page = page;
        this.size = size;
        this.sort = sort;
    }

    public boolean hasPagination() {
        return size != null && size > 0;
    }

    public int effectivePage() {
        return page == null || page < 0 ? 0 : page;
    }

    public int effectiveSize() {
        return size == null || size <= 0 ? 20 : size;
    }

    public String sortBy() {
        return sort != null ? sort.getBy() : null;
    }

    public boolean sortDesc() {
        return sort != null && sort.getOrder() != null && "DESC".equalsIgnoreCase(sort.getOrder());
    }
}
