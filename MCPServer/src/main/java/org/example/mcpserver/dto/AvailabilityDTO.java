package org.example.mcpserver.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.example.mcpserver.repository.domain.AvailabilityStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record AvailabilityDTO(
        String availabilityId,
        String consultantId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate date,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime startTime,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
        LocalTime endTime,
        AvailabilityStatus status
) {}