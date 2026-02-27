package org.example.mcpclient.workflow.subworkflow.service;

import org.springframework.ai.chat.client.ChatClient;

public class ServiceListWorkflow {

    private final ChatClient chatClient;

    public ServiceListWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run() {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool service_list.

                        After the tool returns:

                        If the returned list is empty, output exactly:
                        No services found.

                        Otherwise:
                        Output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ServiceCode  Description

                        Each row format:
                        <serviceCode>  <description>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """)
                .user("List all service definitions.")
                .call()
                .content();
    }
}