package org.example.mcpclient.workflow.subworkflow.assignment;

import org.springframework.ai.chat.client.ChatClient;

public class AssignmentSuggestConsultantsWorkflow {

    private final ChatClient chatClient;

    public AssignmentSuggestConsultantsWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String assignmentId, int limit) {

        return chatClient.prompt()
                .system("""
                        You MUST recommend consultants for an assignment by calling tools.

                        ABSOLUTE RULES:
                        - You MUST actually CALL tools. Do NOT output JSON tool calls. Do NOT assume data.
                        - Final answer must be ONLY a plain text table OR exactly "No matching consultants found."
                        - No explanations, no bullet points, no reasoning text.

                        REQUIRED TOOL CALL ORDER:
                        1) assignment_get_by_id(assignmentId)
                        2) customer_get_by_id(customerId from assignment)
                        3) availability_list_by_date_status(date from assignment, AVAILABLE)
                        4) consultant_get_by_id(consultantId) for each candidate
                        5) consultant_get_services_by_id(consultantId) for each candidate after region+restriction

                        FILTER RULES (MUST APPLY):
                        A) Time coverage:
                           keep consultant if ANY slot covers full assignment interval:
                           slot.startTime <= assignment.startTime AND slot.endTime >= assignment.endTime

                        B) Region:
                           keep consultant ONLY if consultant.regions (semicolon-separated) contains customer.region (case-insensitive exact match).
                           If customer.region is "SE-STH", then consultant must have "SE-STH" among regions.

                        C) Restrictions:
                           EXCLUDE consultant if consultant.restrictions contains assignment.customerId (case-insensitive exact match).

                        D) Required service:
                           keep consultant ONLY if consultant services contains assignment.service (case-insensitive exact match).

                        OUTPUT (STRICT TABLE ONLY):
                        ConsultantId | FirstName | LastName | EmploymentType | CustomerRegion | ConsultantRegions | RequiredService | TimeCover | RegionMatch | ServiceMatch | RestrictionOK

                        Output ONLY rows where:
                        - TimeCover = TRUE
                        - RegionMatch = TRUE
                        - ServiceMatch = TRUE
                        - RestrictionOK = TRUE

                        If zero rows, output exactly: No matching consultants found.
                        Limit: at most %d rows.
                        """.formatted(limit))
                .user("Suggest consultants for assignment " + assignmentId)
                .call()
                .content();
    }
}