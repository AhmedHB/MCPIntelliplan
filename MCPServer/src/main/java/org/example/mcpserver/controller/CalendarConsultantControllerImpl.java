package org.example.mcpserver.controller;

import org.example.mcpserver.dto.CalendarConsultantDTO;
import org.example.mcpserver.service.AssignmentService;
import org.example.mcpserver.service.ConsultantService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;

@RestController
public class CalendarConsultantControllerImpl implements CalendarConsultantController{
    private final ConsultantService consultantService;


    public CalendarConsultantControllerImpl(ConsultantService consultantService) {
        this.consultantService = consultantService;
    }

    @Override
    public ResponseEntity<CalendarConsultantDTO> calendarForAllConsultants(Set<String> service, Set<String> region, Set<String> status, LocalDate fromDate, LocalDate toDate) {
        return ResponseEntity.ok(consultantService.calendarForAllConsultants(service, region, status, fromDate, toDate));
    }

    @Override
    public ResponseEntity<CalendarConsultantDTO> calendarForConsultant(String consultantId, Set<String> service, Set<String> region, Set<String> status, LocalDate fromDate, LocalDate toDate) {
        return ResponseEntity.ok(consultantService.calendarForConsultant(consultantId, service, region, status, fromDate, toDate));
    }
}
