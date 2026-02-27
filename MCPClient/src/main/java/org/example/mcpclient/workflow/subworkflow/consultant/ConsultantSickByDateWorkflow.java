package org.example.mcpclient.workflow.subworkflow.consultant;

import org.springframework.ai.chat.client.ChatClient;

public class ConsultantSickByDateWorkflow {

    private final ChatClient chatClient;

    public ConsultantSickByDateWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String date) {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool availability_list_by_date_status.

                        Call it EXACTLY ONCE with:
                        - date = the provided date (YYYY-MM-DD)
                        - status = SICK

                        After the tool returns:

                        If the list is empty, output exactly:
                        No sick consultants found.

                        Otherwise:
                        Output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        ConsultantId  Date  StartTime  EndTime  Status

                        Each row format:
                        <consultantId>  <date>  <startTime>  <endTime>  <status>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """)
                .user("""
                        List consultants with status SICK on date %s
                        """.formatted(date))
                .call()
                .content();
    }
}