package org.example.mcpclient.workflow.subworkflow.organization;

import org.springframework.ai.chat.client.ChatClient;

public class OrganizationListConsultantsByRegionWorkflow {

    private final ChatClient chatClient;

    public OrganizationListConsultantsByRegionWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String region) {
        return chatClient.prompt()
                .system("""
                        You MUST call the tool organization_list_consultants_by_region.

                        If you cannot call the tool, output ONLY:
                        Tool error: organization_list_consultants_by_region

                        After the tool returns successfully:
                        Output ONLY a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ConsultantId  FirstName  LastName  Regions  EmploymentType

                        One row per consultant:
                        <consultantId>  <firstName>  <lastName>  <regions>  <employmentType>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT add extra text.
                        - Do NOT invent data.
                        - Always respond in English.
                        """)
                .user("List consultants in region " + region)
                .call()
                .content();
    }
}