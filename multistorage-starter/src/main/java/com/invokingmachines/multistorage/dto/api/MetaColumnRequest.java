package com.invokingmachines.multistorage.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaColumnRequest {

    private String table;
    private String name;
    private String alias;
    private String dataType;
    private Boolean readable;
    private Boolean searchable;
}
