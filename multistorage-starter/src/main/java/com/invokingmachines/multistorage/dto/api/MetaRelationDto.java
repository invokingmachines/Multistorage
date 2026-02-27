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

    private String manyTable;
    private String oneTable;
    private String manyColumn;
    private String oneColumn;
    private String name;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
