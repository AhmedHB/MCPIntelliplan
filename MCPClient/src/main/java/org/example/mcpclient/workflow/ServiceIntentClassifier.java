package org.example.mcpclient.workflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern CONS_ID_PATTERN =
            Pattern.compile("\\bCONS_\\d+\\b", Pattern.CASE_INSENSITIVE);

    private final ChatClient classifierClient;
    private final ObjectMapper mapper;

    public ServiceIntentClassifier(ChatClient classifierClient) {
        this.classifierClient = classifierClient;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Command classify(String userInput) {

        String u = userInput == null ? "" : userInput.trim();
        String ul = u.toLowerCase(Locale.ROOT);

        boolean mentionsServices = containsAny(ul,
                "kompetens", "kompetenser",
                "skill", "skills",
                "service", "services",
                "tjänst", "tjänster",
                "competency", "competencies"
        );

        // ============================================================
        // HARD OVERRIDE 1: "Vilka kompetenser har konsult CONS_100086?"
        // -> GET_SERVICES_BY_ID
        // ============================================================
        Matcher m = CONS_ID_PATTERN.matcher(u);
        if (m.find() && mentionsServices) {
            String id = m.group().toUpperCase(Locale.ROOT);
            return new Command("GET_SERVICES_BY_ID", id, null, null, null, null);
        }

        // ============================================================
        // HARD OVERRIDE 2: list all services/skills
        // ============================================================
        if (containsAny(ul,
                "lista alla kompetenser", "visa alla kompetenser", "alla kompetenser",
                "lista alla skills", "visa alla skills",
                "list all skills", "show all skills",
                "list all services", "show all services"
        )) {
            return new Command("LIST_ALL_SERVICES", null, null, null, null, null);
        }

        // ============================================================
        // LLM fallback
        // ============================================================
        String raw = classifierClient.prompt()
                .system("""
                        You classify user intent for the Service domain.

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

                        Rules:
                        - If user asks to list/show all skills/services/competencies -> LIST_ALL_SERVICES.
                        - If input contains a consultant ID like CONS_100086 AND user asks about that consultant's services -> GET_SERVICES_BY_ID.
                        - If user asks about a consultant's services by full name -> GET_SERVICES_BY_NAME.
                        - If user asks which consultants have specific services -> FIND_CONSULTANTS_BY_SERVICES with services[] and matchMode.
                        - Otherwise -> OTHER.

                        Output JSON ONLY.
                        """)
                .user(u)
                .call()
                .content();

        try {
            RawCommand rc = mapper.readValue(raw, RawCommand.class);

            String action = normUpper(rc.action(), "OTHER");
            String consultantId = normUpper(rc.consultantId(), null);
            String firstName = norm(rc.firstName());
            String lastName = norm(rc.lastName());
            List<String> services = normalizeList(rc.services());
            String matchMode = normUpper(rc.matchMode(), null);

            switch (action) {
                case "LIST_ALL_SERVICES" -> {
                    return new Command("LIST_ALL_SERVICES", null, null, null, null, null);
                }
                case "GET_SERVICES_BY_ID" -> {
                    if (consultantId == null) return new Command("OTHER", null, null, null, null, null);
                    return new Command("GET_SERVICES_BY_ID", consultantId, null, null, null, null);
                }
                case "GET_SERVICES_BY_NAME" -> {
                    if (firstName == null || lastName == null) return new Command("OTHER", null, null, null, null, null);
                    return new Command("GET_SERVICES_BY_NAME", null, firstName, lastName, null, null);
                }
                case "FIND_CONSULTANTS_BY_SERVICES" -> {
                    if (services == null || services.isEmpty()) return new Command("OTHER", null, null, null, null, null);
                    if (!"ALL".equals(matchMode) && !"ANY".equals(matchMode)) matchMode = "ANY";
                    return new Command("FIND_CONSULTANTS_BY_SERVICES", null, null, null, services, matchMode);
                }
                default -> {
                    return new Command("OTHER", null, null, null, null, null);
                }
            }

        } catch (Exception e) {
            return new Command("OTHER", null, null, null, null, null);
        }
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private static String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static String normUpper(String s, String fallback) {
        String t = norm(s);
        return t == null ? fallback : t.toUpperCase(Locale.ROOT);
    }

    private static final Set<String> SERVICE_STOPWORDS = Set.of(
            "kompetens", "kompetensen", "kompetenser",
            "skill", "skills",
            "competency", "competencies",
            "service", "services",
            "tjänst", "tjänster",
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
                .filter(s -> !SERVICE_STOPWORDS.contains(s.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();

        return cleaned.isEmpty() ? null : cleaned;
    }
}