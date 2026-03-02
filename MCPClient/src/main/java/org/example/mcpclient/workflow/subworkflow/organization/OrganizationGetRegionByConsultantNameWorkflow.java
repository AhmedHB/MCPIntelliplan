package org.example.mcpclient.workflow.subworkflow.organization;

import org.springframework.ai.chat.client.ChatClient;

public class OrganizationGetRegionByConsultantNameWorkflow {

    private final ChatClient chatClient;

    public OrganizationGetRegionByConsultantNameWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String firstName, String lastName) {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool organization_get_region_by_consultant_name.

                        After the tool returns:
                        Output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ConsultantId  FirstName  LastName  Region

                        Row:
                        <consultantId>  <firstName>  <lastName>  <region>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - If there are multiple matches, output multiple rows.
                        - Always respond in Swedish.
                        """)
                .user("Get region for consultant " + firstName + " " + lastName)
                .call()
                .content();
    }
}