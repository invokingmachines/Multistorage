package com.invokingmachines.multistorage.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaColumnDto {

    private UUID id;
    private String table;
    private String name;
    private String alias;
    private String dataType;
    private Boolean readable;
    private Boolean searchable;
    private Boolean editable;
    private Instant createdAt;
    private Instant updatedAt;
}
