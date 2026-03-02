package org.example.mcpclient.workflow.subworkflow.organization;

import org.springframework.ai.chat.client.ChatClient;

public class OrganizationCountConsultantsByRegionWorkflow {

    private final ChatClient chatClient;

    public OrganizationCountConsultantsByRegionWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String region) {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool organization_count_consultants_by_region.

                        If you cannot call the tool, output ONLY:
                        Tool error: organization_count_consultants_by_region

                        After the tool returns successfully:
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
                        - Do NOT add extra text.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """)
                .user("Count consultants in region " + region)
                .call()
                .content();
    }
}