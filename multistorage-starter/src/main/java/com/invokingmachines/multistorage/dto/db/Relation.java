package com.invokingmachines.multistorage.dto.db;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Relation {
    private String name;
    private String foreignKeyColumn;
    private String referencedTable;
    private String referencedColumn;
    private String updateRule;
    private String deleteRule;
}
