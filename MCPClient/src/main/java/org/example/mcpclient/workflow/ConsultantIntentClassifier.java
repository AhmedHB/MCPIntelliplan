package org.example.mcpclient.workflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConsultantIntentClassifier {

    public record Command(
            String action,
            String consultantId,
            String firstName,
            String lastName,
            LocalDate date
    ) {}

    private record RawCommand(
            String action,
            String consultantId,
            String firstName,
            String lastName,
            String date
    ) {}

    private static final Pattern CONS_ID_PATTERN =
            Pattern.compile("\\bCONS_\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");

    private final ChatClient classifierClient;
    private final ObjectMapper mapper;

    public ConsultantIntentClassifier(ChatClient classifierClient) {
        this.classifierClient = classifierClient;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Command classify(String userInput) {
        String u = userInput == null ? "" : userInput.trim();
        String ul = u.toLowerCase(Locale.ROOT);

        // ============================================================
        // 0) Deterministic pre-routing (no LLM)
        // ============================================================

        // A) If CONS_#### exists -> GET_BY_ID (hard override)
        Matcher mid = CONS_ID_PATTERN.matcher(u);
        if (mid.find()) {
            String id = mid.group().toUpperCase(Locale.ROOT);
            return new Command("GET_BY_ID", id, null, null, null);
        }

        // B) LIST_ALL
        if (containsAny(ul,
                "lista alla konsulter", "visa alla konsulter", "alla konsulter",
                "list all consultants", "show all consultants"
        )) {
            return new Command("LIST_ALL", null, null, null, null);
        }

        // C) FIND_AVAILABLE_BY_DATE
        // triggers on "tillgängliga/lediga/available" + YYYY-MM-DD
        if (containsAny(ul, "tillgänglig", "tillgängliga", "ledig", "lediga", "available", "availability", "kan jobba")) {
            LocalDate d = extractDate(u);
            if (d != null) {
                return new Command("FIND_AVAILABLE_BY_DATE", null, null, null, d);
            }
        }

        // D) LIST_SICK_BY_DATE
        if (containsAny(ul, "sjuk", "sjuka", "sjukskriven", "sjukskrivna", "sick", "sick leave")) {
            LocalDate d = extractDate(u);
            if (d != null) {
                return new Command("LIST_SICK_BY_DATE", null, null, null, d);
            }
        }

        // E) GET_BY_NAME (best effort)
        // Only if user asks for "visa/hämta/profil/info" etc and looks like a full name exists.
        if (containsAny(ul, "visa", "hämta", "profil", "info", "information", "detaljer", "show", "get", "profile", "details")
                && looksLikeFullName(u)) {

            String[] name = extractFirstLastName(u);
            if (name != null) {
                return new Command("GET_BY_NAME", null, name[0], name[1], null);
            }
        }

        // ============================================================
        // 1) LLM fallback (only if needed)
        // ============================================================
        String raw = classifierClient.prompt()
                .system("""
                        You classify user intent for the Consultant domain.
                        Return ONLY valid JSON. No explanations.

                        Schema:
                        {
                          "action": "LIST_ALL|GET_BY_ID|GET_BY_NAME|FIND_AVAILABLE_BY_DATE|LIST_SICK_BY_DATE|OTHER",
                          "consultantId": "string|null",
                          "firstName": "string|null",
                          "lastName": "string|null",
                          "date": "YYYY-MM-DD|null"
                        }

                        Rules:
                        - LIST_ALL for "list/show all consultants".
                        - GET_BY_ID only if a consultantId token like CONS_100086 is present in the user text.
                        - GET_BY_NAME if user asks for consultant details by name (first + last) and no consultantId is present.
                        - FIND_AVAILABLE_BY_DATE if user asks who is available on a date.
                        - LIST_SICK_BY_DATE if user asks who is sick on a date.
                        - Otherwise OTHER.

                        Output JSON ONLY.
                        """)
                .user(u)
                .call()
                .content();

        try {
            RawCommand rc = mapper.readValue(raw, RawCommand.class);

            String action = normUpper(rc.action(), "OTHER");
            String consultantId = norm(rc.consultantId());
            String firstName = norm(rc.firstName());
            String lastName = norm(rc.lastName());
            String dateStr = norm(rc.date());

            // Guard: never accept hallucinated consultantId
            if ("GET_BY_ID".equals(action)) {
                if (consultantId == null) {
                    return new Command("OTHER", null, null, null, null);
                }
                // must appear verbatim in user input
                if (!containsTokenIgnoreCase(u, consultantId)) {
                    return new Command("OTHER", null, null, null, null);
                }
                return new Command("GET_BY_ID", consultantId.toUpperCase(Locale.ROOT), null, null, null);
            }

            if ("LIST_ALL".equals(action)) {
                return new Command("LIST_ALL", null, null, null, null);
            }

            if ("GET_BY_NAME".equals(action)) {
                if (firstName == null || lastName == null) return new Command("OTHER", null, null, null, null);
                return new Command("GET_BY_NAME", null, firstName, lastName, null);
            }

            if ("FIND_AVAILABLE_BY_DATE".equals(action) || "LIST_SICK_BY_DATE".equals(action)) {
                LocalDate d = (dateStr != null) ? LocalDate.parse(dateStr) : null;
                if (d == null) return new Command("OTHER", null, null, null, null);
                return new Command(action, null, null, null, d);
            }

            return new Command("OTHER", null, null, null, null);

        } catch (Exception e) {
            return new Command("OTHER", null, null, null, null);
        }
    }

    // -------------------- helpers --------------------

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

    private static boolean containsTokenIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static LocalDate extractDate(String text) {
        if (text == null) return null;
        Matcher m = DATE_PATTERN.matcher(text);
        if (!m.find()) return null;
        return LocalDate.parse(m.group(1));
    }

    /**
     * Very small heuristic: if the input contains two capitalized words in a row,
     * treat it as a first+last name candidate.
     */
    private static boolean looksLikeFullName(String text) {
        if (text == null) return false;
        // Example: "Karin Håkansson" / "Lovisa Wallin"
        return Pattern.compile("\\b[\\p{Lu}][\\p{L}]+\\s+[\\p{Lu}][\\p{L}]+\\b").matcher(text).find();
    }

    private static String[] extractFirstLastName(String text) {
        if (text == null) return null;

        Matcher m = Pattern.compile("\\b([\\p{Lu}][\\p{L}]+)\\s+([\\p{Lu}][\\p{L}]+)\\b").matcher(text);
        if (!m.find()) return null;

        String first = m.group(1).trim();
        String last = m.group(2).trim();
        if (first.isBlank() || last.isBlank()) return null;

        return new String[]{first, last};
    }
}