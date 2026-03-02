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
                        You MUST call the tool assignment_find_consultants_by_date with:
                        - date = "%s"

                        After the tool returns:

                        If the returned list is empty, output exactly:
                        No consultants found for date %s.

                        Otherwise output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by 2+ spaces.
                        Output ONLY the table.

                        Headers (exactly):
                        ConsultantId  FirstName  LastName  EmploymentType  Services Regions Restrictions

                        Each row format:
                        <consultantId> <FirstName>  <LastName>  <EmploymentType>  <Services> <Regions> <Restrictions>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON or tools.
                        - Always respond in English.
                        """.formatted(date, date))
                .user("List consultants that have assignments on date: " + date)
                .call()
                .content();
    }
}