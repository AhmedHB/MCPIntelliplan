package org.example.mcpclient.dto;

public record CustomerDTO(
        String customerId,
        String customerName,
        String region,
        String requiredServices,
        String riskProfile
) {}