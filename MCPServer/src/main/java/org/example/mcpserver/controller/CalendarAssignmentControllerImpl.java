package org.example.mcpserver.controller;

import org.example.mcpserver.dto.CalendarAssignmentDTO;
import org.example.mcpserver.service.AssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

@RestController
public class CalendarAssignmentControllerImpl implements CalendarAssignmentController{
    private final AssignmentService assignmentService;

    public CalendarAssignmentControllerImpl(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Override
    public ResponseEntity<CalendarAssignmentDTO> calendarForAllAssignments(Set<String> service, Set<String> region, Set<String> status, LocalDate fromDate, LocalDate toDate) {
        return ResponseEntity.ok(assignmentService.calendarForAllAssignments(service, region, status, fromDate, toDate));
    }
}
