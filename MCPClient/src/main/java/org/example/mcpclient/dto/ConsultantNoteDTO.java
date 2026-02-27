package org.example.mcpclient.dto;

public record ConsultantNoteDTO(
        String noteId,
        String consultantId,
        String customerId,
        String assignmentId, // nullable (because assignment is optional)
        String note
) {}