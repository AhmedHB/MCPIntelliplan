package org.example.mcpclient.workflow.subworkflow.assignment;

import org.springframework.ai.chat.client.ChatClient;

public class AssignmentFindConsultantsByDateWorkflow {

    private final ChatClient toolClient;

    public AssignmentFindConsultantsByDateWorkflow(ChatClient toolClient) {
        this.toolClient = toolClient;
    }

    public String run(String date) {

        return toolClient.prompt()
                .system("""
                        You MUST call the tool assignment_find_consultants_by_date.

                        Call it with:
                        - date = the provided date in format YYYY-MM-DD

                        After the tool returns, output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.

                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ConsultantId  FirstName  LastName  EmploymentType

                        Each row format:
                        <consultantId>  <firstName>  <lastName>  <employmentType>

                        If the returned list is empty, output exactly:
                        No consultants found with assignments on date %s.

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT include AssignmentCount.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """.formatted(date))
                .user("""
                        List consultants that have assignments on date %s
                        """.formatted(date))
                .call()
                .content();
    }
}