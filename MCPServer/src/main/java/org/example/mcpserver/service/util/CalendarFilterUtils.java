package org.example.mcpserver.service.util;

import org.example.mcpserver.dto.filter.Status;
import org.example.mcpserver.repository.domain.AssignmentEntity;
import org.example.mcpserver.service.exception.BadRequestException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CalendarFilterUtils {
    private CalendarFilterUtils() {
    }

    public static void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BadRequestException("fromDate måste vara tidigare än eller samma som toDate.");
        }
    }

    public static Set<String> normalizeFilterValues(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public static Set<Status> normalizeStatuses(Set<String> statuses) {
        if (statuses == null || statuses.isEmpty()) return Set.of();

        Set<Status> parsed = new HashSet<>();
        for (String raw : statuses) {
            if (raw == null || raw.trim().isBlank()) continue;
            try {
                parsed.add(Status.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Ogiltig status: " + raw + ". Tillåtna: " + Arrays.toString(Status.values()));
            }
        }
        return parsed;
    }

    public static boolean matchesService(AssignmentEntity assignment, Set<String> services) {
        if (services.isEmpty()) return true;
        if (assignment.getService() == null) return false;
        return services.contains(assignment.getService().trim().toLowerCase(Locale.ROOT));
    }

    public static boolean matchesRegion(AssignmentEntity assignment, Set<String> regions) {
        if (regions.isEmpty()) return true;
        if (assignment.getCustomer() == null || assignment.getCustomer().getRegion() == null) return false;
        return regions.contains(assignment.getCustomer().getRegion().trim().toLowerCase(Locale.ROOT));
    }

    public static boolean matchesStatus(AssignmentEntity assignment, Set<Status> statuses) {
        if (statuses.isEmpty()) return true;
        if (assignment.getStatus() == null) return false;

        try {
            Status current = Status.valueOf(assignment.getStatus().trim().toUpperCase(Locale.ROOT));
            return statuses.contains(current);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static boolean matchesDateRange(AssignmentEntity assignment, LocalDate fromDate, LocalDate toDate) {
        if (assignment.getDate() == null) return false;
        if (fromDate != null && assignment.getDate().isBefore(fromDate)) return false;
        if (toDate != null && assignment.getDate().isAfter(toDate)) return false;
        return true;
    }
}
