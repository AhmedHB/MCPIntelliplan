package org.example.mcpclient.workflow.subworkflow.organization;

import org.springframework.ai.chat.client.ChatClient;

public class OrganizationListRegionsWithCountsWorkflow {

    private final ChatClient chatClient;

    public OrganizationListRegionsWithCountsWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run() {
        return chatClient.prompt()
                .system("""
                        You MUST call the tool organization_list_regions_with_consultant_counts.

                        If you cannot call the tool, output ONLY:
                        Tool error: organization_list_regions_with_consultant_counts

                        After the tool returns successfully:
                        Output ONLY a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        RegionCode  RegionName  ConsultantCount

                        One row per region:
                        <regionCode>  <regionName>  <count>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT add extra text.
                        - Do NOT invent data.
                        - Always respond in English.
                        """)
                .user("List all regions with consultant counts")
                .call()
                .content();
    }
}