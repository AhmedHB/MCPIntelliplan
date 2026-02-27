package org.example.mcpclient.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
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

    private final ChatClient classifierClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConsultantIntentClassifier(ChatClient classifierClient) {
        this.classifierClient = classifierClient;
    }

    public Command classify(String userInput) {

        // ============================================================
        // HARD OVERRIDE: if user explicitly provides CONS_####, do GET_BY_ID
        // This prevents LLM hallucinating consultant IDs.
        // ============================================================
        Matcher m = CONS_ID_PATTERN.matcher(userInput == null ? "" : userInput);
        if (m.find()) {
            String id = m.group().toUpperCase();
            return new Command("GET_BY_ID", id, null, null, null);
        }

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

                        Deterministic rules (HIGH PRIORITY):

                        1) If user asks to list/show all consultants:
                           Swedish: "lista alla konsulter", "visa alla konsulter", "alla konsulter"
                           English: "list all consultants", "show all consultants"
                           -> action = LIST_ALL

                        2) If user asks for consultant details by name (first + last) and NO consultantId is present:
                           Swedish cues: "konsult", "detaljer", "info", "visa", "hämta", "profil"
                           English cues: "consultant", "details", "info", "show", "get", "profile"
                           AND a full person name (first + last) exists
                           -> action = GET_BY_NAME
                           -> firstName + lastName extracted (best effort)

                        3) If user asks which consultants are available on a specific date:
                           Swedish: "tillgängliga", "lediga", "kan jobba"
                           English: "available"
                           AND date in format YYYY-MM-DD exists
                           -> action = FIND_AVAILABLE_BY_DATE
                           -> date = extracted date

                        4) If user asks to list consultants who are sick on a specific date:
                           Swedish: "sjuk", "sjuka", "sjukskriven", "sjukskrivna"
                           English: "sick", "sick leave"
                           AND date in format YYYY-MM-DD exists
                           -> action = LIST_SICK_BY_DATE
                           -> date = extracted date

                        5) Otherwise -> OTHER

                        Output rules:
                        - Always output JSON only.
                        - consultantId must be null unless action is GET_BY_ID.
                        - firstName/lastName must be null unless action is GET_BY_NAME.
                        - date must be null unless action is FIND_AVAILABLE_BY_DATE or LIST_SICK_BY_DATE.
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
            String dateStr = normalize(rc.date());

            // ============================================================
            // GUARD: prevent hallucinated consultantId
            // (Only accept consultantId if it appears verbatim in user input)
            // ============================================================
            if ("GET_BY_ID".equals(action) && consultantId != null) {
                if (userInput == null || !userInput.contains(consultantId)) {
                    return new Command("OTHER", null, null, null, null);
                }
            }

            LocalDate date = null;

            switch (action) {
                case "GET_BY_ID" -> {
                    firstName = null;
                    lastName = null;
                    date = null;
                }
                case "GET_BY_NAME" -> {
                    consultantId = null;
                    date = null;
                }
                case "FIND_AVAILABLE_BY_DATE", "LIST_SICK_BY_DATE" -> {
                    consultantId = null;
                    firstName = null;
                    lastName = null;
                    if (dateStr != null) {
                        date = LocalDate.parse(dateStr); // expects YYYY-MM-DD
                    }
                }
                case "LIST_ALL", "OTHER" -> {
                    consultantId = null;
                    firstName = null;
                    lastName = null;
                    date = null;
                }
                default -> {
                    action = "OTHER";
                    consultantId = null;
                    firstName = null;
                    lastName = null;
                    date = null;
                }
            }

            return new Command(action, consultantId, firstName, lastName, date);

        } catch (Exception e) {
            return new Command("OTHER", null, null, null, null);
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}