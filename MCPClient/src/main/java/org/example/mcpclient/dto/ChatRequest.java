package org.example.mcpclient.dto;

public record ChatRequest(
        String message,
        String prompt,
        String conversationId
) {}
