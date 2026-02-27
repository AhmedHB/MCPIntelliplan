package org.example.mcpclient.workflow.subworkflow.consultant;

import org.springframework.ai.chat.client.ChatClient;

public class ConsultantListWorkflow {

    private final ChatClient chatClient;

    public ConsultantListWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run() {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool consultant_list.

                        After the tool returns, output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.

                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ConsultantId  FirstName  LastName  EmploymentType  Services

                        One row per consultant.

                        If the returned list is empty, output exactly:
                        No consultants found.

                        Rules:
                        - You are NOT allowed to answer without calling consultant_list.
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """)
                .user("List all consultants.")
                .call()
                .content();
    }
}