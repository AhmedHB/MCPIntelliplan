package org.example.mcpclient.workflow.subworkflow.assignment;

import org.springframework.ai.chat.client.ChatClient;

public class AssignmentFindByDateWorkflow {

    private final ChatClient toolClient;

    public AssignmentFindByDateWorkflow(ChatClient toolClient) {
        this.toolClient = toolClient;
    }

    public String run(String date) {

        return toolClient.prompt()
                .system("""
                        You MUST call the tool assignment_find_by_date.

                        Call it with:
                        - date = the provided date in format YYYY-MM-DD

                        After the tool returns, output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.

                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        AssignmentId  CustomerId  ConsultantId  Date/Time  Status

                        Each row format:
                        <assignmentId>  <customerId>  <consultantId>  <date> <startTime>-<endTime>  <status>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - If the returned list is empty, output exactly:
                          No assignments found for date %s.
                        - Always respond in English.
                        """.formatted(date))
                .user("""
                        Find assignments for date %s
                        """.formatted(date))
                .call()
                .content();
    }
}