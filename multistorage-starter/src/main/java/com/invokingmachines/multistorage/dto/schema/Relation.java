package com.invokingmachines.multistorage.dto.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Relation {

    private String targetTableName;
    private String thisColumnName;
    private String targetColumnName;
}
