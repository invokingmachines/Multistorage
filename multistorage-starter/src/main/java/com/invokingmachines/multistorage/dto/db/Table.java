package com.invokingmachines.multistorage.dto.db;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Table {
    private String name;
    private String schema;
    private String catalog;
    private String type;
    private String remarks;
    private List<Column> columns;
    private List<Relation> relations;
}
