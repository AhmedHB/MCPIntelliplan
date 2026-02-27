package org.example.mcpclient.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ServiceIntentClassifier {

    public record Command(
            String action,
            String consultantId,
            String firstName,
            String lastName,
            List<String> services,
            String matchMode
    ) {}

    private record RawCommand(
            String action,
            String consultantId,
            String firstName,
            String lastName,
            List<String> services,
            String matchMode
    ) {}

    private final ChatClient classifierClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ServiceIntentClassifier(ChatClient classifierClient) {
        this.classifierClient = classifierClient;
    }

    public Command classify(String userInput) {

        String raw = classifierClient.prompt()
                .system("""
                        You classify user intent for the Service domain.

                        This domain handles:
                        - Getting services on a consultant record (by id or by name)
                        - Finding consultants who have specific services/skills/competencies
                        - Listing all service definitions

                        Return ONLY valid JSON. No explanations.

                        Schema:
                        {
                          "action": "LIST_ALL_SERVICES|GET_SERVICES_BY_ID|GET_SERVICES_BY_NAME|FIND_CONSULTANTS_BY_SERVICES|OTHER",
                          "consultantId": "string|null",
                          "firstName": "string|null",
                          "lastName": "string|null",
                          "services": ["string"]|null,
                          "matchMode": "ALL|ANY|null"
                        }

                        Deterministic rules (HIGH PRIORITY):

                        0) If user asks to list/show all skills/services/competencies:
                           Swedish: "lista alla kompetenser", "visa alla kompetenser", "alla kompetenser",
                                    "lista alla skills", "visa alla skills"
                           English: "list all skills", "show all skills", "list all services", "show all services"
                           -> action = LIST_ALL_SERVICES

                        Keywords that mean services/skills/competencies:
                        Swedish: "kompetens", "kompetenser", "skills", "tjänster", "service", "services"
                        English: "skill", "skills", "competency", "competencies", "service", "services"

                        Match mode rules:
                        - If user uses Swedish "eller" or English "or" -> matchMode = ANY
                        - If user uses Swedish "och" or English "and" -> matchMode = ALL
                        - If user lists multiple services separated by comma (",") -> matchMode = ALL
                        - If only one service is mentioned -> matchMode = ANY (or ALL, both are equivalent). Use ANY.

                        Action rules:
                        1) If input contains a consultant ID like CONS_100086 AND user asks about that consultant's services/skills/competencies:
                           -> action = GET_SERVICES_BY_ID
                           -> consultantId = extracted ID

                        2) If user asks about a consultant's services/skills/competencies AND provides a person name (first + last) AND no consultantId:
                           -> action = GET_SERVICES_BY_NAME
                           -> firstName + lastName extracted (best effort)

                        3) If user asks "which consultants have" a service/skill/competency (e.g., "Vilka konsulter har ..."):
                           -> action = FIND_CONSULTANTS_BY_SERVICES
                           -> services = extracted list of service tokens (best effort, keep original casing)
                           -> matchMode = derived from wording ("och/and" vs "eller/or" vs commas)

                        4) Otherwise -> OTHER

                        Output rules:
                        - Always output JSON only.
                        - For LIST_ALL_SERVICES: consultantId/firstName/lastName/services/matchMode must all be null.
                        - consultantId must be null unless action is GET_SERVICES_BY_ID.
                        - firstName/lastName must be null unless action is GET_SERVICES_BY_NAME.
                        - services and matchMode must be null unless action is FIND_CONSULTANTS_BY_SERVICES.
                        - For FIND_CONSULTANTS_BY_SERVICES: services must be a non-empty array.
                        """)
                .user(userInput)
                .call()
                .content();

        try {
            RawCommand rc = mapper.readValue(raw, RawCommand.class);

            String action = rc.action() != null
                    ? rc.action().trim().toUpperCase()
                    : "OTHER";

            String consultantId = normalize(rc.consultantId());
            String firstName = normalize(rc.firstName());
            String lastName = normalize(rc.lastName());
            List<String> services = normalizeList(rc.services());
            String matchMode = normalize(rc.matchMode());
            if (matchMode != null) matchMode = matchMode.toUpperCase();

            switch (action) {
                case "LIST_ALL_SERVICES" -> {
                    consultantId = null;
                    firstName = null;
                    lastName = null;
                    services = null;
                    matchMode = null;
                }
                case "GET_SERVICES_BY_ID" -> {
                    firstName = null;
                    lastName = null;
                    services = null;
                    matchMode = null;
                }
                case "GET_SERVICES_BY_NAME" -> {
                    consultantId = null;
                    services = null;
                    matchMode = null;
                }
                case "FIND_CONSULTANTS_BY_SERVICES" -> {
                    consultantId = null;
                    firstName = null;
                    lastName = null;

                    if (services == null || services.isEmpty()) {
                        action = "OTHER";
                        services = null;
                        matchMode = null;
                    } else {
                        if (!"ALL".equals(matchMode) && !"ANY".equals(matchMode)) {
                            matchMode = "ANY";
                        }
                    }
                }
                default -> {
                    action = "OTHER";
                    consultantId = null;
                    firstName = null;
                    lastName = null;
                    services = null;
                    matchMode = null;
                }
            }

            return new Command(action, consultantId, firstName, lastName, services, matchMode);

        } catch (Exception e) {
            return new Command("OTHER", null, null, null, null, null);
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static final Set<String> SERVICE_STOPWORDS = Set.of(
            // domain keywords
            "kompetens", "kompetensen", "kompetenser",
            "skill", "skills",
            "competency", "competencies",
            "service", "services",
            "tjänst", "tjänster",

            // connectors that must never become service tokens
            "och", "eller", "and", "or"
    );

    private static String cleanServiceToken(String s) {
        if (s == null) return null;
        String t = s.trim();
        t = t.replaceAll("^[\"']+|[\"']+$", "");
        t = t.replaceAll("[\\p{Punct}]+$", "");
        return t.trim();
    }

    private static List<String> normalizeList(List<String> items) {
        if (items == null) return null;

        List<String> cleaned = items.stream()
                .filter(Objects::nonNull)
                .map(ServiceIntentClassifier::cleanServiceToken)
                .filter(s -> !s.isBlank())
                .filter(s -> !SERVICE_STOPWORDS.contains(s.toLowerCase()))
                .distinct()
                .toList();

        return cleaned.isEmpty() ? null : cleaned;
    }
}