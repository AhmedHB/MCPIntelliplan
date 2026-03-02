package org.example.mcpserver.dto;

public record RegionByConsultantResponseDTO (
        String consultantId,
        String firstName,
        String lastName,
        String regions
) {}