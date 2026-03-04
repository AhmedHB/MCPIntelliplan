package org.example.mcpserver.controller;

import org.example.mcpserver.dto.CalendarConsultantDTO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Set;
@RequestMapping("/api/calendar")
public interface CalendarConsultantController {

    @GetMapping("/consultants")
    public CalendarConsultantDTO calendarForAllConsultants(
            @RequestParam(required = false) Set<String> service,
            @RequestParam(required = false) Set<String> region,
            @RequestParam(required = false) Set<String> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );

    /**
     * Exempel:
     *  GET /api/calendar/consultants/CONS_100071
     *  GET /api/calendar/consultants/CONS_100071?service=ForkliftOperator&status=CONFIRMED
     *  GET /api/calendar/consultants/CONS_100071?region=SE-STH&region=SE-MAL
     *  GET /api/calendar/consultants/CONS_100071?fromDate=2026-02-20&toDate=2026-02-28
     */
    @GetMapping("/consultants/{consultantId}")
    public CalendarConsultantDTO calendarForConsultant(
            @PathVariable String consultantId,
            @RequestParam(required = false) Set<String> service,
            @RequestParam(required = false) Set<String> region,
            @RequestParam(required = false) Set<String> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    );
}
