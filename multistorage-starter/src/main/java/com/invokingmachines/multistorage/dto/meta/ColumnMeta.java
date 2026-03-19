package com.invokingmachines.multistorage.dto.meta;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ColumnMeta {

    private String name;
    private String alias;
    private String dataType;
    private Boolean readable;
    private Boolean searchable;
}
