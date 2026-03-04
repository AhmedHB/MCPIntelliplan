package org.example.mcpserver.controller;

import org.example.mcpserver.dto.CalendarAssignmentDTO;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

@RestController
public class CalendarAssignmentControllerImpl implements CalendarAssignmentController{
    @Override
    public CalendarAssignmentDTO calendarForAllAssignments(Set<String> service, Set<String> region, Set<String> status, LocalDate fromDate, LocalDate toDate) {
        return null;
    }
}
