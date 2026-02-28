package com.invokingmachines.multistorage.config;

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
            "meta_relation"
    );

    private List<String> ignoreTables = new ArrayList<>();

    public List<String> getEffectiveIgnoreTables() {
        if (ignoreTables.isEmpty()) return DEFAULT_IGNORE_TABLES;
        List<String> result = new ArrayList<>(DEFAULT_IGNORE_TABLES);
        for (String t : ignoreTables) {
            if (t != null && !t.isBlank() && !result.contains(t)) result.add(t);
        }
        return result;
    }
}
