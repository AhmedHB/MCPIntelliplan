package org.example.mcpserver.dto;

public record CustomerDTO(
        String customerId,
        String customerName,
        String region,
        String requiredServices,
        String riskProfile
) {}