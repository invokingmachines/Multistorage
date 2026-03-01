package com.invokingmachines.multistorage.dto.meta;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RelationMeta {

    private String alias;
    private String fromTable;
    private String toTable;
    private String fromColumn;
    private String toColumn;
    private boolean oneToMany;
}
