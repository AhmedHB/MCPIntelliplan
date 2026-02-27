package org.example.mcpclient.workflow;

import org.springframework.ai.chat.client.ChatClient;

public class DomainWorkflow {

    private final ChatClient chatClient;

    public DomainWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String domainSystemPrompt, String userInput) {

        String system = domainSystemPrompt + """

        STRICT EXECUTION CONTRACT:
        - Use tools whenever the question requires system data.
        - NEVER explain JSON or data formats.
        - NEVER describe the structure of the tool output.
        - NEVER provide programming tutorials or code.
        - Return ONLY the final answer to the user's question.

        For queries about assignment status (e.g. "NO_SHOW"):
        1) Call assignment_list
        2) Filter assignments where status equals the requested status (case-insensitive)
        3) Output ONLY matches as lines: assignmentId | customerId | consultantId | date startTime-endTime | status
        4) If none: "No assignments found with status <STATUS>."
        """;

        String first = chatClient
                .prompt()
                .system(system)
                .user(userInput)
                .call()
                .content();

        // Fail-fast: if model starts explaining JSON / tutorials, re-prompt once
        if (looksLikeJsonExplanation(first)) {

            String repairSystem = system + """

            HARD STOP:
            - DO NOT explain anything.
            - DO NOT mention JSON.
            - DO NOT include code.
            - Only output the filtered results or "No assignments found with status <STATUS>."
            """;

            String repairUser = """
            You produced an explanation instead of the requested result.
            Redo the task strictly according to the contract.
            
            Original question: %s
            """.formatted(userInput);

            return chatClient
                    .prompt()
                    .system(repairSystem)
                    .user(repairUser)
                    .call()
                    .content();
        }

        return first;
    }

    private boolean looksLikeJsonExplanation(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("javascript object notation")
                || t.startsWith("this is a json")
                || t.contains("the json data")
                || t.contains("to extract")
                || t.contains("in python")
                || t.contains("json.loads")
                || t.contains("data structure is as follows");
    }
}