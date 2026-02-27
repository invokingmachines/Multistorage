package com.invokingmachines.multistorage.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaRelationRequest {

    private String manyTable;
    private String oneTable;
    private String manyColumn;
    private String oneColumn;
    private String name;
    private Boolean active;
}
