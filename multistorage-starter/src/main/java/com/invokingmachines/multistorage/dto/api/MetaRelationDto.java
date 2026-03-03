package com.invokingmachines.multistorage.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaRelationDto {

    private String fromTable;
    private String toTable;
    private String fromColumn;
    private String toColumn;
    private boolean oneToMany;
    private String alias;
    private String cascadeType;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
