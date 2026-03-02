package org.example.mcpclient.workflow.subworkflow.organization;

import org.springframework.ai.chat.client.ChatClient;

public class OrganizationGetRegionWithMostConsultantsWorkflow {

    private final ChatClient chatClient;

    public OrganizationGetRegionWithMostConsultantsWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run() {
        return chatClient.prompt()
                .system("""
                        You MUST call the tool organization_list_regions_with_consultant_counts.

                        If you cannot call the tool, output ONLY:
                        Tool error: organization_list_regions_with_consultant_counts

                        After the tool returns successfully:
                        Find the region with the highest ConsultantCount.
                        Output ONLY a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        RegionCode  RegionName  ConsultantCount

                        One row:
                        <regionCode>  <regionName>  <count>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT add extra text.
                        - Always respond in English.
                        """)
                .user("Which region has the most consultants?")
                .call()
                .content();
    }
}