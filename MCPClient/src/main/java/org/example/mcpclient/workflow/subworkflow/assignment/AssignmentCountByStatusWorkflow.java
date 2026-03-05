package org.example.mcpclient.workflow.subworkflow.assignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssignmentCountByStatusWorkflow {

    private static final Logger LOG =
            LoggerFactory.getLogger(AssignmentCountByStatusWorkflow.class);

    private static final Pattern INT_PATTERN = Pattern.compile("(\\d+)");

    private final ChatClient toolClient;

    public AssignmentCountByStatusWorkflow(ChatClient toolClient) {
        this.toolClient = toolClient;
    }

    public String run(String status) {
        String normalized = status == null ? "" : status.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z_]", "")
                .trim();

        //LOG.info("COUNT_BY_STATUS raw='{}' normalized='{}'", status, normalized);

        // Force tool use, but ask for ONLY the number
        String raw = toolClient.prompt()
                .system("""
                        You are in the ASSIGNMENT domain.
                        
                        You MUST call this tool exactly once:
                        assignment_count_by_status
                        
                        Do NOT call region tools.
                        Do NOT call organization tools.
                        Do NOT call any other tool.
                        
                        Input parameter:
                        status (example: NO_SHOW)
                        
                        After the tool returns, output ONLY the number.
                        """)
                .user("status=" + normalized)
                .call()
                .content();

        //LOG.info("COUNT_BY_STATUS toolNumberRaw='{}'", raw);

        int n = 0;
        Matcher m = INT_PATTERN.matcher(raw);
        if (m.find()) {
            n = Integer.parseInt(m.group(1));
        }

        return "There are " + n + " assignments with status " + normalized + ".";
    }
}