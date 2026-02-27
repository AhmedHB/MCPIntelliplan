package org.example.mcpclient.dto;

public record ConsultantDTO(
        String consultantId,
        String firstName,
        String lastName,
        String employmentType,
        String services,
        String regions,
        String pools,
        String restrictions,
        String customerExperience
) {}