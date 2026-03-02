package org.example.mcpclient.workflow.subworkflow.organization;

import org.springframework.ai.chat.client.ChatClient;

public class OrganizationGetRegionByConsultantIdWorkflow {

    private final ChatClient chatClient;

    public OrganizationGetRegionByConsultantIdWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String consultantId) {
        return chatClient.prompt()
                .system("""
                        You MUST call the tool organization_get_region_by_consultant_id.

                        After the tool returns:
                        Output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ConsultantId  FirstName  LastName  Region employmentType

                        Each row format:
                        <consultantId>  <firstName>  <lastName>  <region> <employmentType>
     
                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """)
                .user("Get region by consultant id " + consultantId)
                .call()
                .content();
    }
}