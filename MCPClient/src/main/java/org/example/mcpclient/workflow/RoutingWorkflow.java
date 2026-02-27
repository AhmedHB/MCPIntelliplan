package org.example.mcpclient.workflow;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;
import java.util.Objects;

public class RoutingWorkflow {
    private final ChatClient chatClient;

    // ============================================================
    // ROUTE DEFINITIONS (Shared between Router + DomainWorkflow)
    // ============================================================
    public static final Map<String, String> ROUTES = Map.of(

            "consultant",
            """
            You are responsible ONLY for the Consultant Domain.
            Scope:
            - Consultant profiles
            - Availability management
            - Consultant notes
            - Consultant skills
            Always respond in English.
            """,

            "assignment",
            """
            You are responsible ONLY for the Assignment Domain (Core Domain).
            Scope:
            - Create assignments
            - Assign consultants
            - Assignment status management
            - Suggest consultants
            - Validate availability & skills
            
            IMPORTANT:
            - You MUST use available tools when data is required.
            - Do NOT invent data.
            - For status-based questions:
              1) Call assignment_list
              2) Filter by status
              3) Return matching assignments
              
            Always respond in English.
            """,

            "customer",
            """
            You are responsible ONLY for the Customer Domain.
            Scope:
            - Create customers
            - Update customers
            - Delete customers
            - Retrieve customers
            Always respond in English.
            """,

            "service",
            """
            You are responsible ONLY for the Skill/Service Domain.
            Scope:
            - Create skills
            - Remove skills
            - Update skills
            - Identify consultants by skill
            Always respond in English.
            """,

            "organization",
            """
            You are responsible ONLY for the Organization Domain.
            Scope:
            - Manage regions
            - Manage pools
            - Move consultants
            - Analyze distribution
            Always respond in English.
            """,

            "other",
            """
            You are a system guide assistant.
            The request does not clearly match a domain.
            Explain available routes and ask user to clarify.
            Do NOT modify data.
            """
    );

    public RoutingWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String route(String input) {

        String in = input == null ? "" : input;

        // Hard overrides for explicit IDs
        if (in.matches("(?is).*\\bASSIGN_\\d+\\b.*")) {
            return "assignment";
        }
        if (in.matches("(?is).*\\bCONS_\\d+\\b.*")) {
            return "consultant";
        }
        if (in.matches("(?is).*\\bCUST_\\w+\\b.*")) {
            return "customer";
        }

        String keys = String.join(", ", ROUTES.keySet());

        String routingPrompt =
                """
                You are a strict domain router.
        
                Return ONLY ONE route label from the list.
                Output must be exactly one of: [%s]
                Do not add explanations. Do not add extra text.
        
                HIGH PRIORITY RULES (apply before anything else):
                - If the user asks about consultants being sick / on sick leave (Swedish: "sjuk", "sjuka", "sjukskriven", "sjukskrivna";
                  English: "sick", "sick leave") -> Output: consultant
                - If the user asks about availability statuses for consultants (AVAILABLE, BOOKED, SICK, etc.) -> Output: consultant
        
                Domain definitions:
                - assignment: questions about assignments/uppdrag, recommendation for replacement consultant, assignment status (e.g. NO_SHOW, LATE_REPORTED), assigning consultants, creating/updating assignments.
                - consultant: consultant profiles, availability, notes, skills on consultant record.
                - customer: customer records and customer details.
                - service: skills/services/competencies definitions and queries about who has a skill.
                - organization: regions and pools and moving consultants between them.
                - other: anything outside system scope (weather, news, general knowledge).
        
                Examples:
                Input: "Föreslå konsulter till uppdraget ASSIGN_300009"
                Output: assignment
                
                Input: "Vilka uppdrag är i status NO_SHOW?"
                Output: assignment
        
                Input: "Which assignments are in status LATE_REPORTED?"
                Output: assignment
        
                Input: "Lista alla regioner"
                Output: organization
        
                Input: "Lista alla konsulter"
                Output: consultant
        
                Input: "Lista konsulter som är sjuka den 2026-02-25."
                Output: consultant
        
                Input: "Vad är vädret?"
                Output: other
        
                Now classify:
                Input: "%s"
                Output:
                """.formatted(keys, input);

        String raw = chatClient.prompt(routingPrompt)
                .call()
                .content()
                .trim();

        String routeKey = raw.toLowerCase().replaceAll("[^a-z]", "");

        if (!ROUTES.containsKey(routeKey)) {
            routeKey = "other";
        }

        System.out.printf(
                "Input: %s%nRoutes: %s%nRawRoute: %s%nRouteKey: %s%n",
                input, keys, raw, routeKey
        );

        return routeKey;
    }

    private String getRouteSystemPrompt(String routeKey, Map<String, String> routes) {
        return routes.getOrDefault(routeKey, routes.get("other"));
    }
}
