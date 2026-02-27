package org.example.mcpclient.workflow.subworkflow.assignment;

import org.springframework.ai.chat.client.ChatClient;

public class AssignmentSuggestConsultantsDebugWorkflow {

    private final ChatClient chatClient;

    public AssignmentSuggestConsultantsDebugWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /*
        LLM-flödet ska se ut så här:

        AssignmentWorkflow (client)
        ↓
        assignment_get_by_id (server)
        ↓
        availability_list_by_date_status (server)
        ↓
        availability_list_by_consultant_date (server)  ← 🔥 här behövs den
        ↓
        consultant_get_by_id
        ↓
        consultant_get_services_by_id

     */

    public String run(String assignmentId, int limit) {

        int lim = (limit <= 0) ? 5 : Math.min(limit, 50);

        String systemPrompt = """
                You MUST use tools. Do not assume data. Do not invent data.
                
                OUTPUT RULES (ABSOLUTE):
                - Output ONLY the table OR exactly: No matching consultants found.
                - Plain text only. NO markdown. NO backticks. NO code fences.
                - NO pipe characters. Columns separated by 2+ spaces.
                
                REQUIRED TOOL CALL ORDER:
                1) assignment_get_by_id(assignmentId="%s")
                   Extract and store these constants (MUST be used verbatim in EVERY row):
                   - A_CUSTOMER_ID
                   - A_REQUIRED_SERVICE
                   - A_DATE
                   - A_START
                   - A_END
                
                2) customer_get_by_id(customerId=A_CUSTOMER_ID)
                   Extract and store this constant (MUST be used verbatim in EVERY row):
                   - C_REGION
                
                3) availability_list_by_date_status(date=A_DATE, status="AVAILABLE")
                   From this list, extract UNIQUE consultantIds.
                   Do NOT decide TimeCover here.
                
                4) For EACH consultantId from step 3, call:
                   availability_list_by_consultant_date(consultantId=<CID>, date=A_DATE)
                
                   From THIS consultant-specific list:
                   A) TimeCover rule (SINGLE SLOT COVER ONLY):
                      TimeCover=TRUE iff there exists at least one slot where:
                        slot.status == AVAILABLE AND slotStart <= A_START AND slotEnd >= A_END
                      Choose CoverSlotStart/CoverSlotEnd from SUCH a covering slot.
                      If no covering AVAILABLE slot exists -> discard consultant (do not continue).
                
                   B) Conflict rule (BLOCKING OVERLAP):
                      Conflict=TRUE iff there exists at least one slot where:
                        slot.status != AVAILABLE AND overlap(slotStart, slotEnd, A_START, A_END) is TRUE
                      overlap is TRUE iff: slotStart < A_END AND slotEnd > A_START
                      If Conflict=TRUE -> discard consultant (do not continue).
                
                5) consultant_get_by_id(consultantId) ONLY for consultants that remain after step 4.
                
                6) consultant_get_services_by_id(consultantId) ONLY for consultants that remain after step 5.
                
                STRICT COLUMN SOURCE RULES (NO EXCEPTIONS):
                - CustomerId column MUST equal A_CUSTOMER_ID for every row.
                - CustomerRegion column MUST equal C_REGION for every row.
                - RequiredService column MUST equal A_REQUIRED_SERVICE for every row.
                - AssignmentDate/Start/End MUST equal A_DATE / A_START / A_END for every row.
                - ConsultantRegions MUST be copied from consultant_get_by_id.regions (as-is).
                - Restrictions MUST be copied from consultant_get_by_id.restrictions (as-is; if null write NULL).
                
                FLAG COMPUTATION RULES (STRICT):
                - TimeCover MUST be TRUE for every output row.
                - CoverSlotStart/CoverSlotEnd MUST be the start/end of the selected covering AVAILABLE slot from step 4.
                - RegionMatch:
                    TRUE only if ConsultantRegions contains CustomerRegion as an EXACT token (case-insensitive),
                    where ConsultantRegions is ';' separated.
                - RestrictionOK:
                    FALSE if Restrictions contains CustomerId as an EXACT token (case-insensitive),
                    where Restrictions is ';' separated.
                    TRUE otherwise.
                - ServiceMatch:
                    TRUE only if consultant services list contains RequiredService as an EXACT token (case-insensitive).
                    FALSE otherwise.
                
                FILTERING (IMPORTANT):
                - Output ONLY rows where:
                  TimeCover=TRUE AND RegionMatch=TRUE AND RestrictionOK=TRUE AND ServiceMatch=TRUE
                - If none match, output exactly:
                  No matching consultants found.
                
                TABLE HEADER (exactly):
                ConsultantId  FirstName  LastName  EmploymentType  CustomerId  CustomerRegion  ConsultantRegions  Restrictions  RequiredService  AssignmentDate  AssignmentStart  AssignmentEnd  CoverSlotStart  CoverSlotEnd  TimeCover  RegionMatch  RestrictionOK  ServiceMatch
                
                Include up to %d rows.
                """.formatted(assignmentId, lim);

        return chatClient.prompt()
                .system(systemPrompt)
                .user("Debug consultant matching for assignment " + assignmentId)
                .call()
                .content();
    }
}