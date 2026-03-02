package org.example.mcpclient.workflow.subworkflow.customer;

import org.springframework.ai.chat.client.ChatClient;

public class CustomerListWorkflow {

    private final ChatClient chatClient;

    public CustomerListWorkflow(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String run() {

        return chatClient.prompt()
                .system("""
                        You MUST call the tool customer_list.

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
                        """)
                .user("List all customers.")
                .call()
                .content();
    }
}