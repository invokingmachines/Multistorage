package com.invokingmachines.multistorage.dto.meta;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RelationMeta {

    private String name;
    private String childTable;
    private String manyColumn;
    private String oneColumn;
    private String joinCurrentColumn;
    private String joinChildColumn;
}
