package com.invokingmachines.multistorage.dto.schema;

import java.util.Map;
import java.util.Optional;

public interface EntitySchema {

    Optional<Table> getTable(String alias);

    Optional<Table> getTableByName(String tableName);

    static EntitySchema of(Map<String, Table> tablesByAlias) {
        return new EntitySchema() {
            @Override
            public Optional<Table> getTable(String alias) {
                return Optional.ofNullable(tablesByAlias.get(alias));
            }

            @Override
            public Optional<Table> getTableByName(String tableName) {
                return tablesByAlias.values().stream()
                        .filter(t -> tableName.equals(t.getName()))
                        .findFirst();
            }
        };
    }

    static EntitySchema empty() {
        return new EntitySchema() {
            @Override
            public Optional<Table> getTable(String alias) {
                return Optional.empty();
            }

            @Override
            public Optional<Table> getTableByName(String tableName) {
                return Optional.empty();
            }
        };
    }
}
