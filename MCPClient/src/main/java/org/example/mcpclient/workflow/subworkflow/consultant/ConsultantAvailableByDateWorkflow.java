package org.example.mcpclient.workflow.subworkflow.consultant;

import org.springframework.ai.chat.client.ChatClient;

public class ConsultantAvailableByDateWorkflow {

    private final ChatClient chatClient;

    public ConsultantAvailableByDateWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String date) {

        String systemPrompt = """
                You MUST call the tool consultant_available_by_date EXACTLY ONCE.
                
                Call it with EXACT JSON parameters:
                {
                  "date": "%s"
                }
                
                You MUST NOT call any other tools.
                
                After the tool returns:
                - Output a plain text table (NOT markdown).
                - Do NOT use pipe '|' characters.
                - Do NOT use dashed separator lines.
                - Use fixed-width columns separated by 2+ spaces.
                
                Headers (exactly):
                ConsultantId  FirstName  LastName  EmploymentType  Services
                
                If the returned list is empty, output exactly:
                No consultants found available on date %s.
                
                Rules:
                - Use ONLY the tool output.
                - Do NOT invent data.
                - Do NOT explain JSON.
                - Always respond in English.
                """.formatted(date, date);

        return chatClient.prompt()
                .system(systemPrompt)
                .user("Call the tool now.")
                .call()
                .content();
    }
}