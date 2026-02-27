package org.example.mcpclient.workflow.subworkflow.service;

import org.springframework.ai.chat.client.ChatClient;

public class ConsultantServicesByNameWorkflow {

    private final ChatClient chatClient;

    public ConsultantServicesByNameWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String firstName, String lastName) {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool consultant_get_services_by_name.

                        Call it with:
                        - firstName = the provided first name
                        - lastName  = the provided last name

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
                        Get services for consultant:
                        First name: %s
                        Last name: %s
                        """.formatted(firstName, lastName))
                .call()
                .content();
    }
}