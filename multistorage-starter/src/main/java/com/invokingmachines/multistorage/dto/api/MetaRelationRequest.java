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

    private String fromTable;
    private String toTable;
    private String fromColumn;
    private String toColumn;
    private Boolean oneToMany;
    private String alias;
    private Boolean active;
}
