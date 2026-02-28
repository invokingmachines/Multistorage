package com.invokingmachines.multistorage.dto.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Table {

    private String name;
    private String alias;
    private List<Column> columns;
    private List<Relation> relations;

    public Optional<Column> getColumnByAlias(String columnAlias) {
        if (columns == null) return Optional.empty();
        return columns.stream()
                .filter(c -> columnAlias.equals(c.getAlias()))
                .findFirst();
    }

    public Optional<Relation> getRelationByTargetName(String targetTableName) {
        if (relations == null) return Optional.empty();
        return relations.stream()
                .filter(r -> targetTableName.equals(r.getTargetTableName()))
                .findFirst();
    }
}
