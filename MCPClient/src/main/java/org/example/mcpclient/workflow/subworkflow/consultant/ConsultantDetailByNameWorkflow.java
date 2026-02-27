package org.example.mcpclient.workflow.subworkflow.consultant;

import org.springframework.ai.chat.client.ChatClient;

public class ConsultantDetailByNameWorkflow {

    private final ChatClient chatClient;

    public ConsultantDetailByNameWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String firstName, String lastName) {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool consultant_find_by_name.

                        Call it with:
                        - firstName = the provided first name
                        - lastName  = the provided last name

                        After the tool returns:

                        If exactly ONE consultant is returned:
                        - Output a plain text table (NOT markdown).
                        - Do NOT use pipe '|' characters.
                        - Do NOT use dashed separator lines.
                        - Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ConsultantId  FirstName  LastName  EmploymentType  Services

                        Output exactly ONE row.

                        If ZERO consultants are returned, output exactly:
                        Consultant not found.

                        If MORE THAN ONE consultant is returned, output exactly:
                        Multiple consultants found. Please specify consultantId.

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT include AssignmentCount.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """)
                .user("""
                        Get consultant details for:
                        First name: %s
                        Last name: %s
                        """.formatted(firstName, lastName))
                .call()
                .content();
    }
}