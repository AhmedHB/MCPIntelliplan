package org.example.mcpserver.dto.filter;

import java.time.LocalDate;
import java.util.Set;

public record CalendarFilter(
        Set<String> services,          // ex: {"ForkliftOperator","Picker"}  (tom/null => alla)
        Set<String> regions,           // ex: {"SE-STH"} (tom/null => alla)
        Set<Status> statuses, // ex: {CONFIRMED, NO_SHOW} (tom/null => alla)
        LocalDate fromDate,            // null => ingen lower bound
        LocalDate toDate               // null => ingen upper bound
) {
    public boolean isEmpty() {
        return (services == null || services.isEmpty())
                && (regions == null || regions.isEmpty())
                && (statuses == null || statuses.isEmpty())
                && fromDate == null
                && toDate == null;
    }
}