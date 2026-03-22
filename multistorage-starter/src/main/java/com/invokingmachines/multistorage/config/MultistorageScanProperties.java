package com.invokingmachines.multistorage.config;

import com.invokingmachines.multistorage.util.NamingUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "multistorage.scan")
@Data
public class MultistorageScanProperties {

    private static final List<String> DEFAULT_IGNORE_TABLES = List.of(
            "databasechangelog",
            "databasechangeloglock",
            "meta_table",
            "meta_column",
            "meta_relation",
            "meta_feature"
    );

    private static final List<String> DEFAULT_NON_EDITABLE_COLUMN_ALIASES = List.of(
            "id",
            "createdAt",
            "updatedAt"
    );

    private List<String> ignoreTables = new ArrayList<>();

    private List<String> nonEditableColumnAliases = new ArrayList<>();

    public List<String> getEffectiveIgnoreTables() {
        if (ignoreTables.isEmpty()) return DEFAULT_IGNORE_TABLES;
        List<String> result = new ArrayList<>(DEFAULT_IGNORE_TABLES);
        for (String t : ignoreTables) {
            if (t != null && !t.isBlank() && !result.contains(t)) result.add(t);
        }
        return result;
    }

    public List<String> getEffectiveNonEditableColumnAliases() {
        if (nonEditableColumnAliases == null || nonEditableColumnAliases.isEmpty()) {
            return DEFAULT_NON_EDITABLE_COLUMN_ALIASES;
        }
        return List.copyOf(nonEditableColumnAliases);
    }

    public boolean isNonEditableColumn(String columnName, String columnAlias) {
        String name = columnName == null ? "" : columnName.strip();
        String alias = columnAlias == null ? "" : columnAlias.strip();
        String camelFromDbName = name.isEmpty() ? "" : NamingUtils.toCamelCase(name);
        for (String token : getEffectiveNonEditableColumnAliases()) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String t = token.strip();
            if (t.equalsIgnoreCase(name) || t.equalsIgnoreCase(alias)) {
                return true;
            }
            if (!camelFromDbName.isEmpty() && (t.equalsIgnoreCase(camelFromDbName) || camelFromDbName.equalsIgnoreCase(t))) {
                return true;
            }
        }
        return false;
    }

    public boolean defaultEditableForNewColumn(String columnName, String columnAlias) {
        return !isNonEditableColumn(columnName, columnAlias);
    }
}
