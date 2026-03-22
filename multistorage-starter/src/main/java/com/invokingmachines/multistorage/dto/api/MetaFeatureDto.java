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
public class MetaFeatureDto {

    private Long id;
    private String code;
    private String path;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
