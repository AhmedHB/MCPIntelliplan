package org.example.mcpclient.dto;

public record PoolDTO(
        String poolId,
        String description,
        String regions
) {}