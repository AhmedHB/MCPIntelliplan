package org.example.mcpserver.service.util;

import java.time.LocalTime;

public final class TimeRangeUtils {
    private TimeRangeUtils() {
    }

    public static boolean covers(LocalTime slotStart, LocalTime slotEnd, LocalTime start, LocalTime end) {
        if (slotStart == null || slotEnd == null || start == null || end == null) return false;
        return !slotStart.isAfter(start) && !slotEnd.isBefore(end);
    }

    // overlap if intervals intersect: [slotStart, slotEnd) intersects [start, end)
    public static boolean overlaps(LocalTime slotStart, LocalTime slotEnd, LocalTime start, LocalTime end) {
        if (slotStart == null || slotEnd == null || start == null || end == null) return false;
        return slotStart.isBefore(end) && slotEnd.isAfter(start);
    }
}
