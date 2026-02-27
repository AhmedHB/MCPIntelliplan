package org.example.mcpclient.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.util.Locale;

public class AssignmentIntentClassifier {

    public record Command(
            String action,
            String assignmentId,
            String status,
            LocalDate date,
            String name
    ) {
    }

    private record RawCommand(
            String action,
            String assignmentId,
            String status,
            String date,
            String name
    ) {
    }

    private final ChatClient classifierClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public AssignmentIntentClassifier(ChatClient classifierClient) {
        this.classifierClient = classifierClient;
    }

    public Command classify(String userInput) {

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
                        
                        PRIORITY RULES (IMPORTANT):
                        
                        0) If the user asks to suggest/recommend consultants for a specific assignment
                           AND an assignment id like ASSIGN_300009 is present:
                           action = SUGGEST_CONSULTANTS
                           fill: assignmentId
                        
                        1) WORKING_ON_DATE: name + date -> WORKING_ON_DATE
                        
                        2) CONSULTANTS_ON_DATE: date -> CONSULTANTS_ON_DATE
                        
                        3) DATE queries: COUNT_BY_DATE or FIND_BY_DATE
                        
                        4) STATUS queries: COUNT_BY_STATUS or FIND_BY_STATUS
                        
                        5) GET_BY_ID:
                           If the user asks about an assignment and provides an assignmentId like ASSIGN_300009:
                           action = GET_BY_ID
                           fill: assignmentId
                        
                        Extraction rules:
                        - assignmentId must be the token like "ASSIGN_300009"
                        - status token only (e.g. "NO_SHOW")
                        - date must be YYYY-MM-DD
                        """)
                .user(userInput)
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

            // -----------------------------
            // HARD OVERRIDE (prevents OTHER/GET_BY_ID mistakes)
            // -----------------------------
            // If user clearly asks for suggestions + has ASSIGN_ id -> force SUGGEST_CONSULTANTS
            String inputLower = userInput == null ? "" : userInput.toLowerCase(Locale.ROOT);
            if (assignmentId == null) {
                assignmentId = extractAssignFromText(userInput);
            }
            boolean wantsSuggest =
                    inputLower.contains("föreslå") ||
                            inputLower.contains("rekommendera") ||
                            inputLower.contains("förslag") ||
                            inputLower.contains("ersätt") ||
                            inputLower.contains("suggest") ||
                            inputLower.contains("recommend");

            if (wantsSuggest && assignmentId != null) {
                action = "SUGGEST_CONSULTANTS";
            }

            // Enforce required fields per action
            return switch (action) {
                case "SUGGEST_CONSULTANTS" -> (assignmentId == null)
                        ? new Command("OTHER", null, null, null, null)
                        : new Command("SUGGEST_CONSULTANTS", assignmentId, null, null, null);

                case "GET_BY_ID" -> (assignmentId == null)
                        ? new Command("OTHER", null, null, null, null)
                        : new Command("GET_BY_ID", assignmentId, null, null, null);

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
            // fallback: try deterministic extraction for suggest even if JSON parse failed
            String assignmentId = extractAssignFromText(userInput);
            String inputLower = userInput == null ? "" : userInput.toLowerCase(Locale.ROOT);
            boolean wantsSuggest =
                    inputLower.contains("föreslå") || inputLower.contains("suggest") || inputLower.contains("recommend");

            if (wantsSuggest && assignmentId != null) {
                return new Command("SUGGEST_CONSULTANTS", assignmentId, null, null, null);
            }
            return new Command("OTHER", null, null, null, null);
        }
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

    // removes trailing punctuation, quotes, whitespace
    private static String cleanToken(String s) {
        if (s == null) return null;
        String t = s.trim();
        t = t.replaceAll("^[\"']+|[\"']+$", "");          // strip quotes
        t = t.replaceAll("[\\p{Punct}]+$", "");           // strip trailing punctuation
        return t.trim();
    }

    private static String cleanIdToken(String s) {
        String t = cleanToken(s);
        if (t == null) return null;
        // keep only valid id chars (ASSIGN_ + digits)
        // e.g. "ASSIGN_300009," -> "ASSIGN_300009"
        var m = java.util.regex.Pattern.compile("(ASSIGN_\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(t);
        return m.find() ? m.group(1) : t;
    }

    private static String extractAssignFromText(String text) {
        if (text == null) return null;
        var m = java.util.regex.Pattern
                .compile("(ASSIGN_\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : null;
    }
}