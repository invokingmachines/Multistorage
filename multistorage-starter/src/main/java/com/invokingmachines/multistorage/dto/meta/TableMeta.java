package com.invokingmachines.multistorage.dto.meta;


import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.Map;

@Data
@Builder
public class TableMeta {

    private String name;
    private String alias;
    @Singular
    private Map<String, ColumnMeta> columns;
    @Singular
    private Map<String, RelationMeta> relations;
}
