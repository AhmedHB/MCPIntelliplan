package org.example.mcpclient.workflow.subworkflow.customer;

import org.springframework.ai.chat.client.ChatClient;

public class CustomerSearchWorkflow {

    private final ChatClient chatClient;

    public CustomerSearchWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String customerName, String region, String riskProfile) {

        String name = customerName == null ? "null" : "\"" + customerName + "\"";
        String reg = region == null ? "null" : "\"" + region + "\"";
        String rp = riskProfile == null ? "null" : "\"" + riskProfile + "\"";

        return chatClient.prompt()
                .system("""
                        You MUST call the tool customer_search EXACTLY ONCE.

                        Call it with parameters:
                        - customerName = %s
                        - region = %s
                        - riskProfile = %s

                        After the tool returns:
                        - If the returned list is empty, output exactly:
                          No customers found.

                        Otherwise output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        CustomerId  CustomerName  Region  RequiredServices  RiskProfile

                        Each row:
                        <customerId>  <customerName>  <region>  <requiredServices>  <riskProfile>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """.formatted(name, reg, rp))
                .user("Search customers now.")
                .call()
                .content();
    }
}