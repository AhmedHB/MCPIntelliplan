package org.example.mcpclient.workflow.subworkflow.service;

import org.springframework.ai.chat.client.ChatClient;

public class ConsultantServicesByIdWorkflow {

    private final ChatClient chatClient;

    public ConsultantServicesByIdWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String consultantId) {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool consultant_get_services_by_id.

                        Call it with:
                        - consultantId = the provided consultant id

                        After the tool returns:

                        If the returned list is empty, output exactly:
                        No services found.

                        Otherwise:
                        Output a plain text list (NOT markdown).
                        Do NOT use bullet points.
                        Do NOT use numbering.
                        Do NOT explain JSON.
                        Output one service per line.

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Always respond in English.
                        """)
                .user("""
                        Get services for consultant with id %s
                        """.formatted(consultantId))
                .call()
                .content();
    }
}