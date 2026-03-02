package org.example.mcpclient.workflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrganizationIntentClassifier {

    public record Command(
            String action,
            String consultantId,
            String firstName,
            String lastName,
            String region
    ) {}

    private record RawCommand(
            String action,
            String consultantId,
            String firstName,
            String lastName,
            String region
    ) {}

    private static final Pattern CONS_ID_PATTERN =
            Pattern.compile("\\bCONS_\\d+\\b", Pattern.CASE_INSENSITIVE);

    // Capture "i region X" / "in region X" (single token, e.g. Stockholm or SE-STH)
    private static final Pattern REGION_QUERY_PATTERN =
            Pattern.compile("\\b(i|in)\\s+region\\s+([\\p{L}0-9\\-]+)\\b", Pattern.CASE_INSENSITIVE);

    private final ChatClient classifierClient;
    private final ObjectMapper mapper;

    public OrganizationIntentClassifier(ChatClient classifierClient) {
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

        // 0A) LIST REGIONS:
        // "Vilka regioner finns?" / "Lista regioner" / "Show regions"
        // Must be checked BEFORE other region logic.
        if (containsAny(ul,
                "vilka regioner finns",
                "vilka regioner",
                "lista regioner",
                "alla regioner",
                "list regions",
                "which regions",
                "show regions",
                "regions exist"
        )) {
            return new Command("LIST_REGIONS", null, null, null, null);
        }

        // 0B) LIST ALL REGIONS WITH COUNTS:
        // "Lista alla regioner och antal konsulter per region."
        // Must be checked BEFORE count-by-single-region.
        if (containsAny(ul,
                "lista alla regioner",
                "alla regioner och antal",
                "antal konsulter per region",
                "per region",
                "regions and counts",
                "counts per region",
                "consultant counts per region"
        ) && containsAny(ul, "region")) {
            return new Command("LIST_REGIONS_WITH_COUNTS", null, null, null, null);
        }

        // 0C) COUNT consultants by region: "Hur många konsulter finns i region Linköping?"
        // Must be checked BEFORE LIST consultants by region.
        if (containsAny(ul,
                "hur många", "hur manga", "how many", "antal", "number of"
        ) && containsAny(ul, "konsult", "konsulter", "consultant", "consultants")
                && containsAny(ul, "region")) {

            String region = extractRegionToken(u);
            if (region != null) {
                return new Command("COUNT_CONSULTANTS_BY_REGION", null, null, null, region);
            }
        }

        // 0D) LIST consultants by region: "Vilka konsulter är i region Stockholm/SE-STH?"
        if (containsAny(ul,
                "vilka konsulter", "konsulter i region", "consultants in region",
                "in region", "i region"
        )) {
            String region = extractRegionToken(u);
            if (region != null) {
                return new Command("LIST_CONSULTANTS_BY_REGION", null, null, null, region);
            }
        }

        // 0E) Which region belongs to a consultant (by CONS id or by name)
        if (containsAny(ul, "region", "vilken region", "tillhör", "tillhor")) {

            Matcher mid = CONS_ID_PATTERN.matcher(u);
            if (mid.find()) {
                String id = mid.group().toUpperCase(Locale.ROOT);
                return new Command("GET_REGION_BY_CONSULTANT_ID", id, null, null, null);
            }

            if (looksLikeFullName(u)) {
                String[] name = extractFirstLastName(u);
                if (name != null) {
                    return new Command("GET_REGION_BY_CONSULTANT_NAME", null, name[0], name[1], null);
                }
            }
        }

        // ============================================================
        // 1) LLM fallback (only if needed)
        // ============================================================
        String raw = classifierClient.prompt()
                .system("""
                        You classify user intent for the Organization domain.
                        Return ONLY valid JSON. No explanations.

                        Schema:
                        {
                          "action": "LIST_REGIONS|LIST_REGIONS_WITH_COUNTS|GET_REGION_BY_CONSULTANT_ID|GET_REGION_BY_CONSULTANT_NAME|LIST_CONSULTANTS_BY_REGION|COUNT_CONSULTANTS_BY_REGION|OTHER",
                          "consultantId": "string|null",
                          "firstName": "string|null",
                          "lastName": "string|null",
                          "region": "string|null"
                        }

                        Rules:
                        - LIST_REGIONS if user asks which regions exist (e.g. "Vilka regioner finns?").
                        - LIST_REGIONS_WITH_COUNTS if user asks to list all regions with consultant counts.
                        - COUNT_CONSULTANTS_BY_REGION if user asks how many consultants are in a specific region.
                        - LIST_CONSULTANTS_BY_REGION if user asks which consultants are in a region.
                        - GET_REGION_BY_CONSULTANT_ID only if a consultantId token like CONS_100086 exists AND user asks about region for that consultant.
                        - GET_REGION_BY_CONSULTANT_NAME if user asks about region for a consultant by first+last name and no consultantId exists.
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
            String region = norm(rc.region());

            // 0A) REGION WITH MOST CONSULTANTS:
            if (containsAny(ul,
                    "flest konsulter",
                    "mest konsulter",
                    "most consultants",
                    "highest number of consultants",
                    "largest number of consultants"
            ) && containsAny(ul, "region")) {
                return new Command("GET_REGION_WITH_MOST_CONSULTANTS", null, null, null, null);
            }

            if ("LIST_REGIONS".equals(action)) {
                return new Command("LIST_REGIONS", null, null, null, null);
            }

            if ("LIST_REGIONS_WITH_COUNTS".equals(action)) {
                return new Command("LIST_REGIONS_WITH_COUNTS", null, null, null, null);
            }

            if ("COUNT_CONSULTANTS_BY_REGION".equals(action)) {
                if (region == null) return new Command("OTHER", null, null, null, null);
                return new Command("COUNT_CONSULTANTS_BY_REGION", null, null, null, region);
            }

            if ("LIST_CONSULTANTS_BY_REGION".equals(action)) {
                if (region == null) return new Command("OTHER", null, null, null, null);
                return new Command("LIST_CONSULTANTS_BY_REGION", null, null, null, region);
            }

            if ("GET_REGION_BY_CONSULTANT_ID".equals(action)) {
                if (consultantId == null) return new Command("OTHER", null, null, null, null);
                if (!containsTokenIgnoreCase(u, consultantId)) return new Command("OTHER", null, null, null, null);
                return new Command("GET_REGION_BY_CONSULTANT_ID", consultantId.toUpperCase(Locale.ROOT), null, null, null);
            }

            if ("GET_REGION_BY_CONSULTANT_NAME".equals(action)) {
                if (firstName == null || lastName == null) return new Command("OTHER", null, null, null, null);
                return new Command("GET_REGION_BY_CONSULTANT_NAME", null, firstName, lastName, null);
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

    private static boolean looksLikeFullName(String text) {
        if (text == null) return false;
        return Pattern.compile("\\b[\\p{Lu}][\\p{L}]+\\s+[\\p{Lu}][\\p{L}]+\\b")
                .matcher(text).find();
    }

    private static String[] extractFirstLastName(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("\\b([\\p{Lu}][\\p{L}]+)\\s+([\\p{Lu}][\\p{L}]+)\\b").matcher(text);
        if (!m.find()) return null;
        return new String[]{m.group(1).trim(), m.group(2).trim()};
    }

    private static String extractRegionToken(String text) {
        if (text == null) return null;

        Matcher m = REGION_QUERY_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(2).trim();
        }

        // fallback: "region Stockholm"
        Matcher m2 = Pattern.compile("\\bregion\\s+([\\p{L}0-9\\-]+)\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m2.find()) return m2.group(1).trim();

        return null;
    }
}