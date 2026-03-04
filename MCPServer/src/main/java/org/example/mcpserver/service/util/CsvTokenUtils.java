package org.example.mcpserver.service.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class CsvTokenUtils {
    private CsvTokenUtils() {
    }

    public static String cleanToken(String value) {
        if (value == null) return null;
        String token = value.trim();

        if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
            token = token.substring(1, token.length() - 1).trim();
        }

        token = token.replaceAll("[\\p{Punct}]+$", "");
        return token.trim();
    }

    public static List<String> splitSemicolonTokens(String value) {
        if (value == null) return List.of();
        String token = value.trim();
        if (token.isBlank()) return List.of();

        return Arrays.stream(token.split(";"))
                .map(String::trim)
                .map(CsvTokenUtils::cleanToken)
                .filter(v -> v != null && !v.isBlank())
                .toList();
    }

    public static boolean containsIgnoreCaseToken(String semicolonSeparatedValues, String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        String needle = candidate.trim().toLowerCase(Locale.ROOT);
        return splitSemicolonTokens(semicolonSeparatedValues).stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .anyMatch(v -> v.equals(needle));
    }

    public static Set<String> toUpperTokenSet(String semicolonSeparatedValues) {
        return splitSemicolonTokens(semicolonSeparatedValues).stream()
                .map(v -> v.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
