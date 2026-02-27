package org.example.mcpclient.workflow.subworkflow.assignment;

import org.springframework.ai.chat.client.ChatClient;

public class AssignmentSuggestConsultantsbyCodeWorkflow {
    private final ChatClient chatClient;

    public AssignmentSuggestConsultantsbyCodeWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String assignmentId, int limit) {

        return chatClient.prompt()
                .system("""
                            You MUST call the tool assignment_suggest_consultants.
                        
                            Then output a plain text table (NOT markdown).
                            Do NOT use pipe '|' characters.
                            Use fixed-width columns with spaces.
                        
                            Headers:
                            ConsultantId  FirstName  LastName  EmploymentType  Regions  RequiredService
                        
                            Rules:
                            - Use only the tool output. Do not invent data.
                            - Do not explain JSON.
                            - If the returned list is empty, output exactly: "No matching consultants found."
                            - Always respond in English.
                        """)
                .user("Suggest consultants for assignment " + assignmentId + " (limit " + limit + ")")
                .call()
                .content();
    }
}
