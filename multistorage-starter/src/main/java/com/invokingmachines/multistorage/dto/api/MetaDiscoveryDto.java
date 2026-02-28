package com.invokingmachines.multistorage.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaDiscoveryDto {

    private List<TableDiscoveryDto> tables;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableDiscoveryDto {
        private String alias;
        private String name;
        private List<String> relations;
        private List<ColumnDiscoveryDto> selectableColumns;
        private List<ColumnDiscoveryDto> searchableColumns;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnDiscoveryDto {
        private String alias;
        private String name;
        private String dataType;
    }
}
