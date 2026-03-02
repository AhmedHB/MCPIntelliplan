package org.example.mcpserver.dto;

public record ConsultantCountByRegionDTO(
        String regionCode,
        String regionName,
        long count
) {}
