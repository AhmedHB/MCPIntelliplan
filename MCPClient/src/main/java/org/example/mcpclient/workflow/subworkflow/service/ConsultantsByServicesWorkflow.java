package org.example.mcpclient.workflow.subworkflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

public class ConsultantsByServicesWorkflow {

    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsultantsByServicesWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(List<String> services, String matchMode) {

        String servicesJson;
        try {
            servicesJson = mapper.writeValueAsString(services);
        } catch (JsonProcessingException e) {
            servicesJson = "[]";
        }

        String mode = (matchMode == null || matchMode.isBlank())
                ? "ANY"
                : matchMode.trim().toUpperCase();

        return chatClient.prompt()
                .system("""
                        You MUST call the tool service_find_consultants_by_services.

                        Call it EXACTLY with these parameters:
                        - services = %s
                        - matchMode = "%s"

                        After the tool returns:

                        If the returned list is empty, output exactly:
                        No consultants found.

                        Otherwise:
                        Output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ConsultantId  FirstName  LastName  EmploymentType

                        Each row format:
                        <consultantId>  <firstName>  <lastName>  <employmentType>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """.formatted(servicesJson, mode))
                .user("""
                        Find consultants by services.
                        """)
                .call()
                .content();
    }
}