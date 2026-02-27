package org.example.mcpclient.workflow.subworkflow.assignment;

import org.springframework.ai.chat.client.ChatClient;

public class AssignmentCountByDateWorkflow {

    private final ChatClient toolClient;

    public AssignmentCountByDateWorkflow(ChatClient toolClient) {
        this.toolClient = toolClient;
    }

    public String run(String date) {
        return toolClient.prompt()
                .system("""
                        You MUST call the tool assignment_count_by_date with the provided date (YYYY-MM-DD).
                        Return ONLY one sentence in English:
                        "There are <N> assignments on <DATE>."
                        No extra text.
                        """)
                .user("Count assignments for date: " + date)
                .call()
                .content();
    }
}