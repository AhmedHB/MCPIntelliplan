package org.example.mcpclient.workflow.subworkflow.consultant;

import org.springframework.ai.chat.client.ChatClient;

public class ConsultantDetailByIdWorkflow {

    private final ChatClient chatClient;

    public ConsultantDetailByIdWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String consultantId) {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool consultant_get_by_id.

                        Call it EXACTLY ONCE with:
                        - consultantId = the provided consultant id

                        After the tool returns:

                        If no consultant is found, output exactly:
                        Consultant not found.

                        Otherwise:
                        Return ONLY a plain text table (NOT markdown).
                        Do NOT add any introduction, explanation, bullet list, or extra lines.
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ConsultantId  FirstName  LastName  EmploymentType  Services  Regions  Restrictions

                        Exactly ONE row format:
                        <consultantId>  <firstName>  <lastName>  <employmentType>  <services>  <regions>  <restrictions>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT include AssignmentCount.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """)
                .user("""
                        Get consultant with id %s
                        """.formatted(consultantId))
                .call()
                .content();
    }
}