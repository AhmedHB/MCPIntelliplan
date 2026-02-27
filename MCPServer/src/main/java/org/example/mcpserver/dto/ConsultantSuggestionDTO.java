package org.example.mcpserver.dto;

public record ConsultantSuggestionDTO(
        String consultantId,
        String firstName,
        String lastName,
        String employmentType,
        String regions,
        String matchedService
) {}
