package org.example.mcpclient.workflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomerIntentClassifier {

    public record Command(
            String action,
            String customerId,
            String customerName,
            String region,
            String riskProfile
    ) {}

    private record RawCommand(
            String action,
            String customerId,
            String customerName,
            String region,
            String riskProfile
    ) {}

    private static final Pattern CUSTOMER_ID = Pattern.compile("\\bCUST_[A-Z0-9_]+\\b");
    private static final Pattern REGION_CODE = Pattern.compile("\\bSE-[A-Z]{3}\\b");
    private static final Pattern RISK = Pattern.compile("\\b(LOW|MEDIUM|HIGH)\\b", Pattern.CASE_INSENSITIVE);

    // Common Swedish city/region cues -> region codes (extend freely)
    private static String tryMapRegionNameToCode(String ul) {
        // keep it simple: substring checks
        if (ul.contains("stockholm")) return "SE-STH";
        if (ul.contains("sthlm")) return "SE-STH";

        if (ul.contains("malmö") || ul.contains("malmo")) return "SE-MAL";

        if (ul.contains("göteborg") || ul.contains("goteborg")) return "SE-GOT";

        if (ul.contains("uppsala")) return "SE-UPP";

        if (ul.contains("linköping") || ul.contains("linkoping")) return "SE-LIN";

        if (ul.contains("nyköping") || ul.contains("nykoping")) return "SE-NYK";

        return null;
    }

    private final ChatClient classifierClient;
    private final ObjectMapper mapper;

    public CustomerIntentClassifier(ChatClient classifierClient) {
        this.classifierClient = classifierClient;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Command classify(String userInput) {

        // -----------------------------
        // 0) Deterministic pre-routing
        // -----------------------------
        String u = userInput == null ? "" : userInput.trim();
        String ul = u.toLowerCase(Locale.ROOT);

        // --- Extract filter signals FIRST (so they beat LIST_ALL) ---
        boolean mentionsRegionWord = ul.contains("region") || ul.contains("i region") || ul.contains("inom region");
        boolean mentionsRisk = ul.contains("risk") || ul.contains("riskprofil") || ul.contains("risk profile");
        boolean mentionsName = ul.contains("namn") || ul.contains("kundnamn") || ul.contains("name");

        // Region can be either explicit code (SE-XXX) OR a known place name (Stockholm, etc.)
        String region = null;
        Matcher rm = REGION_CODE.matcher(u.toUpperCase(Locale.ROOT));
        if (rm.find()) {
            region = rm.group();
        } else {
            region = tryMapRegionNameToCode(ul);
        }

        // Risk profile
        String risk = null;
        Matcher riskm = RISK.matcher(u);
        if (riskm.find()) risk = riskm.group(1).toUpperCase(Locale.ROOT);

        // If ANY filter intent exists OR we managed to extract a region/risk -> SEARCH
        // (Even if region/risk is null, we still allow SEARCH if user clearly asks filter; LLM can fill customerName.)
        if (region != null || risk != null || mentionsRegionWord || mentionsRisk || mentionsName) {
            // If user only said "region" but no code/name, let LLM fill later (SEARCH) via fallback.
            // But if we already extracted region/risk, we can return SEARCH immediately.
            if (region != null || risk != null) {
                return new Command("SEARCH", null, null, region, risk);
            }
            // Otherwise continue to LLM fallback (it can extract customerName/region/risk)
        }

        // GET_BY_ID (if a customerId token exists)
        Matcher idm = CUSTOMER_ID.matcher(u);
        if (idm.find() && containsAny(ul, "visa", "hämta", "info", "information", "kund", "customer", "show", "get", "details")) {
            return new Command("GET_BY_ID", idm.group(), null, null, null);
        }

        // LIST_ALL customers (ONLY if no filter intent)
        if (containsAny(ul,
                "lista alla kunder", "visa alla kunder", "alla kunder",
                "list all customers", "show all customers")) {
            return new Command("LIST_ALL", null, null, null, null);
        }

        // -----------------------------
        // 1) LLM fallback (only if needed)
        // -----------------------------
        String raw = classifierClient.prompt()
                .system("""
                        You classify user intent for the Customer domain.
                        Return ONLY valid JSON. No extra text.

                        Schema:
                        {
                          "action": "LIST_ALL|GET_BY_ID|SEARCH|OTHER",
                          "customerId": "string|null",
                          "customerName": "string|null",
                          "region": "string|null",
                          "riskProfile": "LOW|MEDIUM|HIGH|null"
                        }

                        Rules:
                        - If user asks to list all customers -> LIST_ALL.
                        - If a token like CUST_WAREHOUSE_1 is present AND user asks about that customer -> GET_BY_ID.
                        - If user asks to filter by name/region/risk profile -> SEARCH with extracted fields.
                        - If uncertain -> OTHER.

                        Output JSON ONLY.
                        """)
                .user(u)
                .call()
                .content();

        try {
            RawCommand rc = mapper.readValue(raw, RawCommand.class);

            String action = normUpper(rc.action(), "OTHER");
            String customerId = norm(rc.customerId());
            String customerName = norm(rc.customerName());
            String llmRegion = normUpper(rc.region(), null);
            String riskProfile = normUpper(rc.riskProfile(), null);

            // Prefer deterministic extracted region/risk if we already found them
            if (llmRegion == null) llmRegion = region;
            if (riskProfile == null) riskProfile = risk;

            return switch (action) {
                case "LIST_ALL" -> new Command("LIST_ALL", null, null, null, null);
                case "GET_BY_ID" -> (customerId == null)
                        ? new Command("OTHER", null, null, null, null)
                        : new Command("GET_BY_ID", customerId, null, null, null);
                case "SEARCH" -> (customerName == null && llmRegion == null && riskProfile == null)
                        ? new Command("LIST_ALL", null, null, null, null)
                        : new Command("SEARCH", null, customerName, llmRegion, riskProfile);
                default -> new Command("OTHER", null, null, null, null);
            };

        } catch (Exception e) {
            return new Command("OTHER", null, null, null, null);
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
}