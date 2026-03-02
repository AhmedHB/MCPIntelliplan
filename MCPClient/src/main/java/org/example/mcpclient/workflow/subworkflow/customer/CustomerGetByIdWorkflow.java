package org.example.mcpclient.workflow.subworkflow.customer;

import org.springframework.ai.chat.client.ChatClient;

public class CustomerGetByIdWorkflow {

    private final ChatClient chatClient;

    public CustomerGetByIdWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run(String customerId) {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool customer_get_by_id.

                        After the tool returns:
                        Output a plain text table (NOT markdown).
                        Do NOT use pipe '|' characters.
                        Do NOT use dashed separator lines.
                        Use fixed-width columns separated by spaces.

                        Headers (exactly):
                        CustomerId  CustomerName  Region  RequiredServices  RiskProfile

                        Row:
                        <customerId>  <customerName>  <region>  <requiredServices>  <riskProfile>

                        Rules:
                        - Use ONLY the tool output.
                        - Do NOT invent data.
                        - Do NOT explain JSON.
                        - Always respond in English.
                        """)
                .user("Get customer by id " + customerId)
                .call()
                .content();
    }
}