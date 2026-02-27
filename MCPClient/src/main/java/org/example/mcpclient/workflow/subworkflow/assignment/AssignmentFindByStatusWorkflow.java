package org.example.mcpclient.workflow.subworkflow.assignment;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;

public class AssignmentFindByStatusWorkflow {

    private final ChatClient toolClient;

    public AssignmentFindByStatusWorkflow(ChatClient toolClient) {
        this.toolClient = toolClient;
    }

    public String run(String status) {

        String normalized = status.toUpperCase(Locale.ROOT);

        return toolClient.prompt()
                .system("""
                        You MUST call the tool assignment_find_by_status.

                        Call it with:
                        - status = the provided status (case-insensitive, already normalized)

                        Do NOT call assignment_list.

                        After the tool returns, output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.

                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        AssignmentId  CustomerId  ConsultantId  Date/Time  Status

                        Each row format:
                        <assignmentId>  <customerId>  <consultantId>  <date> <startTime>-<endTime>  <status>

                        If the returned list is empty, output exactly:
                        No assignments found with status %s.

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """.formatted(normalized))
                .user("""
                        Find assignments with status %s
                        """.formatted(normalized))
                .call()
                .content();
    }
}