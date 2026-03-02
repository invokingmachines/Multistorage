package com.invokingmachines.multistorage.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class NamingUtils {

    private NamingUtils() {}

    public static String toPascalCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isBlank()) return snakeCase;
        return Arrays.stream(snakeCase.split("_"))
                .filter(p -> !p.isEmpty())
                .map(p -> p.substring(0, 1).toUpperCase() + p.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }

    public static String toCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isBlank()) return snakeCase;
        String pascal = toPascalCase(snakeCase);
        return pascal.substring(0, 1).toLowerCase() + pascal.substring(1);
    }

    public static String uncapitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    public static String toPathSegment(String pascalOrCamel) {
        if (pascalOrCamel == null || pascalOrCamel.isBlank()) return pascalOrCamel;
        return pascalOrCamel
                .replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                .toLowerCase();
    }

    public static String fromPathSegment(String pathSegment) {
        if (pathSegment == null || pathSegment.isBlank()) return pathSegment;
        return Arrays.stream(pathSegment.split("-"))
                .filter(p -> !p.isEmpty())
                .map(p -> p.substring(0, 1).toUpperCase() + p.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }
}
