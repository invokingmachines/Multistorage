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
public class MetaTableDto {

    private UUID id;
    private String name;
    private String alias;
    private Instant createdAt;
    private Instant updatedAt;
}
