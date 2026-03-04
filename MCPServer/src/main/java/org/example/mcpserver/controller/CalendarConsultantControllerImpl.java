package org.example.mcpserver.controller;

import org.example.mcpserver.dto.CalendarConsultantDTO;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

@RestController
public class CalendarConsultantControllerImpl implements CalendarConsultantController{
    @Override
    public CalendarConsultantDTO calendarForAllConsultants(Set<String> service, Set<String> region, Set<String> status, LocalDate fromDate, LocalDate toDate) {
        return null;
    }

    @Override
    public CalendarConsultantDTO calendarForConsultant(String consultantId, Set<String> service, Set<String> region, Set<String> status, LocalDate fromDate, LocalDate toDate) {
        return null;
    }
}
