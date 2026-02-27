package org.example.mcpclient.workflow.subworkflow.assignment;

import org.springframework.ai.chat.client.ChatClient;

public class ConsultantWorkingOnDateWorkflow {

    private final ChatClient toolClient;

    public ConsultantWorkingOnDateWorkflow(ChatClient toolClient) {
        this.toolClient = toolClient;
    }

    public String run(String fullName, String date) {
        return toolClient.prompt()
                .system("""
                        You answer whether a consultant is working on a given date.
        
                        You MUST use tools. Do NOT explain your reasoning.

                        Steps:
                            1) Split the name into firstName and lastName.
                            2) Call consultant_find_by_name(firstName,lastName).
                            3) If 0 matches: reply ONLY "I cannot find that consultant."
                            4) If >1 matches: reply ONLY "Which consultantId do you mean?"
                            5) If exactly 1 match: call assignment_is_consultant_working_on_date(consultantId,date).
                            6) Reply ONLY with EXACTLY one of:
                        - "Yes, <FirstName> <LastName> is working on <DATE>."
                                - "No, <FirstName> <LastName> is not working on <DATE>."
                        
                        Output rules (CRITICAL):
                        - Output EXACTLY ONE sentence and nothing else.
                        - No preambles like "Since..." or "We call..."
                                - Always respond in English.
                        """)
                .user("Name: " + fullName + ", Date: " + date)
                .call()
                .content();
    }
}