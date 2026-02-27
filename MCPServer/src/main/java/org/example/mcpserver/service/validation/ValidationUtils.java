package org.example.mcpserver.service.validation;

import org.example.mcpserver.service.exception.BadRequestException;

import java.time.LocalTime;
import java.util.Arrays;

public final class ValidationUtils {

    private ValidationUtils() {}

    public static <T> T requireNonNull(T value, String field) {
        if (value == null) throw new BadRequestException(field + " får inte vara null.");
        return value;
    }

    public static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException(field + " får inte vara tom.");
        }
        return value.trim();
    }

    public static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Validerar en komma-separerad lista (ex "A,B,C") så att den inte innehåller tomma items.
     * Null är OK (fältet är valfritt).
     */
    public static void requireCsvNoEmptyItems(String csv, String field) {
        if (csv == null) return;

        boolean hasEmpty = Arrays.stream(csv.split(","))
                .map(String::trim)
                .anyMatch(s -> s.isEmpty());

        if (hasEmpty) {
            throw new BadRequestException(field + " innehåller tomma värden. Ex: \"A,,B\" är inte tillåtet.");
        }
    }

    public static void requireTimeRange(LocalTime start, LocalTime end, String startField, String endField) {
        requireNonNull(start, startField);
        requireNonNull(end, endField);
        if (!end.isAfter(start)) {
            throw new BadRequestException(endField + " måste vara efter " + startField + ".");
        }
    }
}