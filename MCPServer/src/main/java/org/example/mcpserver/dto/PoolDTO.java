package org.example.mcpserver.dto;

public record PoolDTO(
        String poolId,
        String description,
        String regions
) {}