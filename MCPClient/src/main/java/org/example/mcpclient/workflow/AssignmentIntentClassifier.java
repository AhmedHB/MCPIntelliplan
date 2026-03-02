package org.example.mcpclient.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssignmentIntentClassifier {

    public record Command(
            String action,
            String assignmentId,
            String status,
            LocalDate date,
            String name
    ) {}

    private record RawCommand(
            String action,
            String assignmentId,
            String status,
            String date,
            String name
    ) {}

    private static final Pattern ASSIGN_ID = Pattern.compile("\\bASSIGN_\\d+\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    // Anpassa listan till dina faktiska statusar
    private static final Set<String> KNOWN_STATUSES = Set.of(
            "NO_SHOW", "CONFIRMED", "LATE_REPORTED", "CANCELLED"
    );

    // matchar tokens som NO_SHOW, LATE_REPORTED osv
    private static final Pattern STATUS_TOKEN = Pattern.compile("\\b[A-Z]+(?:_[A-Z]+)+\\b");

    private final ChatClient classifierClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public AssignmentIntentClassifier(ChatClient classifierClient) {
        this.classifierClient = classifierClient;
    }

    public Command classify(String userInput) {

        String input = userInput == null ? "" : userInput.trim();
        String ul = input.toLowerCase(Locale.ROOT);

        // =====================================================
        // 0) Deterministic pre-routing (hårda regler)
        // =====================================================

        // --- Suggest consultants override ---
        String assignIdFromText = extractAssignId(input);
        boolean wantsSuggest =
                containsAny(ul, "föreslå", "rekommendera", "förslag", "suggest", "recommend");

        if (wantsSuggest && assignIdFromText != null) {
            return new Command("SUGGEST_CONSULTANTS", assignIdFromText, null, null, null);
        }

        // --- STATUS OVERRIDE (NYTT) ---
        String statusFromText = extractKnownStatus(input);
        boolean mentionsStatusWord =
                ul.contains("status") || ul.contains("i status") || ul.contains("med status");

        if (statusFromText != null || mentionsStatusWord) {
            // count?
            boolean wantsCount = containsAny(ul, "hur många", "antal", "count", "number of");
            if (statusFromText != null) {
                return new Command(wantsCount ? "COUNT_BY_STATUS" : "FIND_BY_STATUS",
                        null, statusFromText, null, null);
            }
            // Om de sa “i status …” men vi inte kunde extrahera en känd status -> låt LLM försöka,
            // men vi har i alla fall ökat chansen att inte hamna i OTHER.
        }

        // --- GET_BY_ID (deterministiskt om de frågar efter ett specifikt uppdrag) ---
        boolean asksAboutAssignment =
                containsAny(ul, "visa", "hämta", "uppgifter", "info", "information", "get", "show", "details");

        if (asksAboutAssignment && assignIdFromText != null) {
            return new Command("GET_BY_ID", assignIdFromText, null, null, null);
        }

        LocalDate dateFromText = extractDateFromText(input);

        // DATE OVERRIDE
        if (dateFromText != null) {
            boolean wantsCount = containsAny(ul, "hur många", "antal", "count", "number of");

            // Om de frågar "vilka konsulter är på uppdrag datum ..." -> CONSULTANTS_ON_DATE
            boolean wantsConsultantsOnDate =
                    containsAny(ul, "vilka konsulter", "konsulter", "consultants") &&
                            containsAny(ul, "på uppdrag", "har uppdrag", "assignments on", "have assignments");

            if (wantsConsultantsOnDate) {
                return new Command("CONSULTANTS_ON_DATE", null, null, dateFromText, null);
            }

            // Annars: uppdrag på datum -> FIND/COUNT_BY_DATE
            return new Command(wantsCount ? "COUNT_BY_DATE" : "FIND_BY_DATE",
                    null, null, dateFromText, null);
        }

        // =====================================================
        // 1) LLM fallback
        // =====================================================
        String raw = classifierClient.prompt()
                .system("""
                        You classify user intent for the Assignment domain.
                        Return ONLY valid JSON, no extra text.

                        Schema:
                        {
                          "action": "GET_BY_ID|SUGGEST_CONSULTANTS|FIND_BY_STATUS|COUNT_BY_STATUS|FIND_BY_DATE|COUNT_BY_DATE|CONSULTANTS_ON_DATE|WORKING_ON_DATE|OTHER",
                          "assignmentId": "string|null",
                          "status": "string|null",
                          "date": "YYYY-MM-DD|null",
                          "name": "string|null"
                        }

                        PRIORITY RULES:
                        0) Suggest consultants + assignmentId -> SUGGEST_CONSULTANTS
                        1) WORKING_ON_DATE: name + date
                        2) CONSULTANTS_ON_DATE: date
                        3) DATE queries: COUNT_BY_DATE or FIND_BY_DATE
                        4) STATUS queries: COUNT_BY_STATUS or FIND_BY_STATUS (status token example: NO_SHOW)
                        5) GET_BY_ID: if asking about an assignment + assignmentId

                        Output JSON only.
                        """)
                .user(input)
                .call()
                .content();

        try {
            RawCommand rc = mapper.readValue(raw, RawCommand.class);

            String action = rc.action() != null ? rc.action().trim().toUpperCase(Locale.ROOT) : "OTHER";
            String assignmentId = normalizeUpper(cleanIdToken(rc.assignmentId()));
            String status = normalizeUpper(cleanToken(rc.status()));
            String name = normalize(rc.name());
            String dateStr = normalize(cleanToken(rc.date()));
            LocalDate date = (dateStr != null) ? LocalDate.parse(dateStr) : null;

            // extra säkerhet: om LLM gav status men action OTHER → rätta till
            if ("OTHER".equals(action) && status != null && KNOWN_STATUSES.contains(status)) {
                boolean wantsCount = containsAny(ul, "hur många", "antal", "count", "number of");
                action = wantsCount ? "COUNT_BY_STATUS" : "FIND_BY_STATUS";
            }

            return switch (action) {
                case "SUGGEST_CONSULTANTS" -> (assignmentId == null && assignIdFromText != null)
                        ? new Command("SUGGEST_CONSULTANTS", assignIdFromText, null, null, null)
                        : (assignmentId == null ? new Command("OTHER", null, null, null, null)
                        : new Command("SUGGEST_CONSULTANTS", assignmentId, null, null, null));

                case "GET_BY_ID" -> (assignmentId == null && assignIdFromText != null)
                        ? new Command("GET_BY_ID", assignIdFromText, null, null, null)
                        : (assignmentId == null ? new Command("OTHER", null, null, null, null)
                        : new Command("GET_BY_ID", assignmentId, null, null, null));

                case "FIND_BY_STATUS", "COUNT_BY_STATUS" -> (status == null)
                        ? new Command("OTHER", null, null, null, null)
                        : new Command(action, null, status, null, null);

                case "FIND_BY_DATE", "COUNT_BY_DATE", "CONSULTANTS_ON_DATE" -> (date == null)
                        ? new Command("OTHER", null, null, null, null)
                        : new Command(action, null, null, date, null);

                case "WORKING_ON_DATE" -> (date == null || name == null)
                        ? new Command("OTHER", null, null, null, null)
                        : new Command("WORKING_ON_DATE", null, null, date, name);

                default -> new Command("OTHER", null, null, null, null);
            };

        } catch (Exception e) {
            // sista fallback: status från text
            String st = extractKnownStatus(input);
            if (st != null) {
                boolean wantsCount = containsAny(ul, "hur många", "antal", "count", "number of");
                return new Command(wantsCount ? "COUNT_BY_STATUS" : "FIND_BY_STATUS",
                        null, st, null, null);
            }
            return new Command("OTHER", null, null, null, null);
        }
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static String normalizeUpper(String s) {
        String t = normalize(s);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    private static String cleanToken(String s) {
        if (s == null) return null;
        String t = s.trim();
        t = t.replaceAll("^[\"']+|[\"']+$", "");
        t = t.replaceAll("[\\p{Punct}]+$", "");
        return t.trim();
    }

    private static String cleanIdToken(String s) {
        String t = cleanToken(s);
        if (t == null) return null;
        Matcher m = ASSIGN_ID.matcher(t);
        return m.find() ? m.group().toUpperCase(Locale.ROOT) : t;
    }

    private static String extractAssignId(String text) {
        if (text == null) return null;
        Matcher m = ASSIGN_ID.matcher(text);
        return m.find() ? m.group().toUpperCase(Locale.ROOT) : null;
    }

    private static String extractKnownStatus(String text) {
        if (text == null) return null;
        Matcher m = STATUS_TOKEN.matcher(text.toUpperCase(Locale.ROOT));
        while (m.find()) {
            String token = m.group();
            if (KNOWN_STATUSES.contains(token)) return token;
        }
        return null;
    }

    private static LocalDate extractDateFromText(String text) {
        if (text == null) return null;
        Matcher m = ISO_DATE.matcher(text);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(m.group());
        } catch (Exception e) {
            return null;
        }
    }
}