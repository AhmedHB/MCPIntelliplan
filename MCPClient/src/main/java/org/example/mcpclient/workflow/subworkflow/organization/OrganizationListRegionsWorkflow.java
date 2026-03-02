package org.example.mcpclient.workflow.subworkflow.organization;

import org.springframework.ai.chat.client.ChatClient;

public class OrganizationListRegionsWorkflow {

    private final ChatClient chatClient;

    public OrganizationListRegionsWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run() {
        return chatClient.prompt()
                .system("""
                        You MUST call the tool organization_list_regions.

                        If you cannot call the tool, output ONLY:
                        Tool error: organization_list_regions

                        After the tool returns successfully:
                        Output ONLY a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        RegionCode  RegionName

                        One row per region:
                        <regionCode>  <regionName>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT add extra text.
                        - Do NOT invent data.
                        - Always respond in English.
                        """)
                .user("List all regions")
                .call()
                .content();
    }
}