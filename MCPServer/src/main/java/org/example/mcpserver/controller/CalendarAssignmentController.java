package org.example.mcpserver.controller;

import org.example.mcpserver.dto.CalendarAssignmentDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Set;

@RequestMapping("/api/calendar")
public interface CalendarAssignmentController {
    /**
     * Exempel:
     *  GET /api/calendar/assignments
     *  GET /api/calendar/assignments?service=ForkliftOperator&status=CONFIRMED
     *  GET /api/calendar/assignments?region=SE-STH&region=SE-MAL
     *  GET /api/calendar/assignments?fromDate=2026-02-20&toDate=2026-02-28
     */
    @GetMapping("/assignments")
    CalendarAssignmentDTO calendarForAllAssignments(
            @RequestParam(required = false) Set<String> service,
            @RequestParam(required = false) Set<String> region,
            @RequestParam(required = false) Set<String> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );
}
