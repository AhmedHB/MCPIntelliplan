package org.example.mcpserver.dto;

import java.util.List;

public record CalendarConsultantRowDTO(ConsultantDTO consultant, CustomerDTO customer, List<AssignmentDTO> assignments, List<AvailabilityDTO> availabilities) {
}
