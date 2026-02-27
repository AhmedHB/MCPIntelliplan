package org.example.mcpclient.workflow.subworkflow.assignment;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;

public class AssignmentCountByStatusWorkflow {

    private final ChatClient toolClient;

    public AssignmentCountByStatusWorkflow(ChatClient toolClient) {
        this.toolClient = toolClient;
    }

    public String run(String status) {
        String normalized = status.toUpperCase(Locale.ROOT);

        return toolClient.prompt()
                .system("""
                        You MUST call the tool assignment_count_by_status with the provided status.
                        Return ONLY a single English sentence:
                        "There are <N> assignments with status <STATUS>."
                        Do not explain JSON. Do not add extra text.
                        """)
                .user("Count assignments with status: " + normalized)
                .call()
                .content();
    }
}