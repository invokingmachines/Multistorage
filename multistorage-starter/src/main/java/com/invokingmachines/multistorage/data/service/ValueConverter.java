package com.invokingmachines.multistorage.data.service;

import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class ValueConverter {

    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public Object toJdbcValue(Object value, String dataType) {
        if (value == null) return null;
        String lower = dataType != null ? dataType.toLowerCase() : "";
        if (lower.contains("timestamp")) {
            return toTimestamp(value);
        }
        if (lower.contains("date") && !lower.contains("timestamp")) {
            return value instanceof String ? LocalDate.parse((String) value) : value;
        }
        if (lower.contains("int") || lower.contains("bigint") || lower.contains("smallint") || lower.contains("serial")) {
            return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
        }
        if (lower.contains("float") || lower.contains("numeric") || lower.contains("decimal")) {
            return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
        }
        if (lower.contains("boolean") || lower.contains("bool")) {
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        }
        return value;
    }

    private Object toTimestamp(Object value) {
        Instant instant = null;
        if (value instanceof Instant) {
            instant = (Instant) value;
        } else if (value instanceof java.util.Date) {
            instant = ((java.util.Date) value).toInstant();
        } else if (value instanceof String) {
            String s = (String) value;
            try {
                instant = OffsetDateTime.parse(s, ISO_OFFSET).toInstant();
            } catch (Exception e1) {
                try {
                    instant = Instant.parse(s);
                } catch (Exception e2) {
                    try {
                        instant = LocalDate.parse(s).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
                    } catch (Exception e3) {
                        instant = Instant.parse(s + "Z");
                    }
                }
            }
        } else {
            return value;
        }
        return Timestamp.from(instant);
    }

    public Map<String, Object> convertEntityValues(Map<String, Object> entity, Map<String, String> columnDataTypeByName) {
        if (entity == null || columnDataTypeByName == null) return entity;
        return entity.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            String dt = columnDataTypeByName.get(e.getKey());
                            return dt != null ? toJdbcValue(e.getValue(), dt) : e.getValue();
                        },
                        (a, b) -> b,
                        java.util.LinkedHashMap::new));
    }
}
