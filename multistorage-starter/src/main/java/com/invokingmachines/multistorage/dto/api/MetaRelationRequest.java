package com.invokingmachines.multistorage.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaRelationRequest {

    private UUID id;
    private String fromTable;
    private String toTable;
    private String fromColumn;
    private String toColumn;
    private Boolean oneToMany;
    private String alias;
    private String cascadeType;
    private Boolean active;
}
