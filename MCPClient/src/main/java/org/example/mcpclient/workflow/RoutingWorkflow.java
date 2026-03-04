package org.example.mcpclient.workflow;

import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class RoutingWorkflow {

    private final ChatClient chatClient;

    // ============================================================
    // ROUTE DEFINITIONS
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

    // ============================================================
    // Patterns
    // ============================================================
    private static final Pattern ASSIGN_ID =
            Pattern.compile("\\bASSIGN_\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern CONS_ID =
            Pattern.compile("\\bCONS_\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern CUST_ID =
            Pattern.compile("\\bCUST_[A-Z0-9_]+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern ISO_DATE =
            Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    // NEW: org keywords (keeps old behavior, adds a narrow override)
    private static final Pattern REGION_WORD =
            Pattern.compile("\\b(region|regionen|regioner)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern POOL_WORD =
            Pattern.compile("\\b(pool|pools)\\b", Pattern.CASE_INSENSITIVE);

    public RoutingWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String route(String input) {

        String in = input == null ? "" : input.trim();
        String ul = in.toLowerCase(Locale.ROOT);

        // ============================================================
        // 0) Deterministic hard routing (beats LLM)
        // ============================================================

        // 0.1 LIST ALL CONSULTANTS
        if (containsAny(ul,
                "lista alla konsulter",
                "visa alla konsulter",
                "alla konsulter",
                "list all consultants",
                "show all consultants"
        )) {
            return logAndReturn(in, "consultant", "consultant list-all override");
        }

        // 0.2 Assignment by explicit id
        if (ASSIGN_ID.matcher(in).find()) {
            return logAndReturn(in, "assignment", "ASSIGN id override");
        }

        // 0.3 Working-on-date (Jobbar X den YYYY-MM-DD?)
        boolean hasDate = ISO_DATE.matcher(in).find();
        boolean asksWorking = containsAny(ul, "jobbar", "arbetar", "working") && hasDate;
        if (asksWorking) {
            return logAndReturn(in, "assignment", "working-on-date override");
        }

        // 0.4 Assignment phrasing (MUST be before consultant)
        if (containsAny(ul,
                "uppdrag",
                "på uppdrag",
                "till uppdraget",
                "uppdrag datum",
                "uppdraget",
                "vilka uppdrag",
                "hur många uppdrag",
                "status",
                "no_show",
                "late_reported",
                "sick_reported",
                "confirmed",
                "assignment",
                "assignments"
        )) {
            return logAndReturn(in, "assignment", "assignment keyword override");
        }

        // 0.5 Customer by id
        if (CUST_ID.matcher(in).find()) {
            return logAndReturn(in, "customer", "CUST id override");
        }

        // 0.6 CONS + skills/services intent -> service (MUST be before CONS id override)
        if (CONS_ID.matcher(in).find()) {
            if (containsAny(ul,
                    "kompetens", "kompetenser",
                    "skill", "skills",
                    "service", "services",
                    "tjänst", "tjänster",
                    "competency", "competencies"
            )) {
                return logAndReturn(in, "service", "CONS+skills override");
            }
        }

        // 0.6.5 NEW: CONS + region/pool intent -> organization (MUST be before CONS id override)
        if (CONS_ID.matcher(in).find()) {
            boolean asksRegionOrPool =
                    REGION_WORD.matcher(in).find()
                            || POOL_WORD.matcher(in).find()
                            || containsAny(ul,
                            "vilken region", "tillhör region", "tillhor region",
                            "vilken pool", "tillhör pool", "tillhor pool",
                            "organization", "organisation"
                    );

            if (asksRegionOrPool) {
                return logAndReturn(in, "organization", "CONS+region/pool override");
            }
        }

        // 0.7 Consultant by id (only if not assignment context)
        if (CONS_ID.matcher(in).find()) {
            if (!containsAny(ul, "uppdrag", "assignment", "på uppdrag")) {
                return logAndReturn(in, "consultant", "CONS id override");
            }
        }

        // 0.8 Consultant details by name (fix for: "Visa uppgifter om konsult Karin Håkansson?")
        // If the user says "konsult/consultant" AND asks for details/profile/info/show
        // AND it doesn't look like an assignment query -> consultant
        boolean mentionsConsultantWord = containsAny(ul, "konsult", "consultant");
        boolean asksConsultantDetails = containsAny(ul,
                "visa", "uppgifter", "info", "information", "detaljer", "profil",
                "show", "details", "info", "profile", "get"
        );

        // best-effort "name-like" check: at least two words total and contains consultant keyword
        boolean looksLikeNameQuery = mentionsConsultantWord && countWords(in) >= 4;
        // ex: "Visa uppgifter om konsult Karin Håkansson?" -> 6 words

        if (mentionsConsultantWord && asksConsultantDetails && looksLikeNameQuery) {
            return logAndReturn(in, "consultant", "consultant name/details override");
        }

        // 0.9 Customer phrasing
        if (containsAny(ul, "kund", "kunder", "customer", "customers", "riskprofil", "risk profile")) {
            return logAndReturn(in, "customer", "customer phrasing override");
        }

        // 0.10 Service phrasing
        if (containsAny(ul, "kompetens", "kompetenser", "skill", "skills", "service", "services", "tjänst", "tjänster")) {
            return logAndReturn(in, "service", "service phrasing override");
        }

        // 0.11 Organization phrasing (UPDATED: include region, regionen)
        if (containsAny(ul, "pool", "pools", "organisation", "organization", "region", "regionen", "regioner")) {
            return logAndReturn(in, "organization", "organization phrasing override");
        }

        // 0.12 Consultant sick / availability
        if (containsAny(ul,
                "sjuk", "sjuka", "sjukskriven", "sjukskrivna",
                "sick", "sick leave",
                "tillgänglig", "tillgängliga",
                "available", "availability",
                "booked"
        )) {
            return logAndReturn(in, "consultant", "consultant availability override");
        }

        // ============================================================
        // 1) LLM fallback
        // ============================================================

        String keys = String.join(", ", ROUTES.keySet());

        String routingPrompt = """
                You are a strict domain router.
                Return ONLY ONE route label from the list:
                [%s]
                Do not add explanations.

                Rules:
                - Assignment/uppdrag/jobbar + date -> assignment
                - Consultant availability/sick or consultant profile/details -> consultant
                - Customer questions -> customer
                - Skills/services -> service
                - Regions/pools -> organization
                - Otherwise -> other

                Output:
                """.formatted(keys);

        String raw = chatClient.prompt()
                .system(routingPrompt)
                .user(in)
                .call()
                .content();

        if (raw == null || raw.isBlank()) {
            return logAndReturn(in, "other", "blank LLM route");
        }

        raw = raw.trim();

        String routeKey = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (!ROUTES.containsKey(routeKey)) {
            routeKey = "other";
        }

        System.out.printf(
                "Input: %s%nRoutes: %s%nRawRoute: %s%nRouteKey: %s%n",
                in, keys, raw, routeKey
        );

        return routeKey;
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) {
            if (s.contains(n)) return true;
        }
        return false;
    }

    private static int countWords(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.isBlank()) return 0;
        return t.split("\\s+").length;
    }

    private static String logAndReturn(String input, String routeKey, String reason) {
        System.out.printf("Input: %s%nRouteKey: %s (%s)%n", input, routeKey, reason);
        return routeKey;
    }
}
