package org.example.mcpclient.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mcpclient.McpClientApplication;
import org.example.mcpclient.dto.ChatRequest;
import org.example.mcpclient.workflow.*;
import org.example.mcpclient.workflow.subworkflow.assignment.*;
import org.example.mcpclient.workflow.subworkflow.consultant.*;
import org.example.mcpclient.workflow.subworkflow.service.ConsultantServicesByIdWorkflow;
import org.example.mcpclient.workflow.subworkflow.service.ConsultantServicesByNameWorkflow;
import org.example.mcpclient.workflow.subworkflow.service.ConsultantsByServicesWorkflow;
import org.example.mcpclient.workflow.subworkflow.service.ServiceListWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
public class ChatService {
    private static final Logger LOG =
            LoggerFactory.getLogger(ChatService.class);

    private ChatClient.Builder chatClientBuilder;
    private ToolCallbackProvider tools;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       ToolCallbackProvider tools) {
        this.chatClientBuilder = chatClientBuilder;
        this.tools = tools;
    }

    /*
    ===============================================================
    DOMAIN – DESCRIPTION
    ===============================================================

    +----------------------+--------------------------------------+------------------+
    | Domän                | Services                             | Typ              |
    +----------------------+--------------------------------------+------------------+
    | Consultant Domain    | Consultant, ConsultantNote,          | Core Support     |
    |                      | Availability                         |                  |
    +----------------------+--------------------------------------+------------------+
    | Assignment Domain    | Assignment                           | 🔥 Core Domain   |
    +----------------------+--------------------------------------+------------------+
    | Customer Domain      | Customer                             | Supporting       |
    +----------------------+--------------------------------------+------------------+
    | Skill/Service Domain | Service                              | Supporting       |
    +----------------------+--------------------------------------+------------------+
    | Organization Domain  | Region, Pool                         | Supporting       |
    +----------------------+--------------------------------------+------------------+

    Beskrivning:
    - Assignment Domain är systemets kärndomän och innehåller den centrala
      affärslogiken för resursplanering och matchning.
    - Consultant Domain stödjer kärndomänen genom att hantera konsultdata
      och tillgänglighet.
    - Övriga domäner är stödjande och tillhandahåller strukturell och
      kompletterande information.
    */

    // ============================================================
    // MAIN WORKFLOW
    /*  ex
            User
            → Routing (consultant)
               → ConsultantIntentClassifier
                  → action = FIND_AVAILABLE_BY_DATE
                     → ConsultantAvailableByDateWorkflow
                        → tool: consultant_available_by_date(date)
        */
    // ============================================================

    public String chat(ChatRequest request) {
        String input = request.message();

        try {
            LOG.info("User Input: " + input);

            // Routing client (NO TOOLS)
            ChatClient routingClient = chatClientBuilder.build();
            RoutingWorkflow routingWorkflow = new RoutingWorkflow(routingClient);
            String routeKey = routingWorkflow.route(input);

            LOG.info("Route: " + routeKey);

            // Tool client (WITH TOOLS)
            ChatClient toolClient = chatClientBuilder
                    .defaultToolCallbacks(tools)
                    .build();

            if ("assignment".equals(routeKey)) {
                AssignmentIntentClassifier classifier = new AssignmentIntentClassifier(routingClient);
                var cmd = classifier.classify(input);

                LOG.info("Assignment Action: " + cmd.action());

                String answer = switch (cmd.action()) {
                    case "COUNT_BY_STATUS" -> new AssignmentCountByStatusWorkflow(toolClient).run(cmd.status());
                    case "FIND_BY_STATUS" -> new AssignmentFindByStatusWorkflow(toolClient).run(cmd.status());
                    case "COUNT_BY_DATE" -> new AssignmentCountByDateWorkflow(toolClient)
                            .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    case "FIND_BY_DATE" -> new AssignmentFindByDateWorkflow(toolClient)
                            .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    case "CONSULTANTS_ON_DATE" -> new AssignmentFindConsultantsByDateWorkflow(toolClient)
                            .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    case "WORKING_ON_DATE" -> new ConsultantWorkingOnDateWorkflow(toolClient)
                            .run(cmd.name(), cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    //AssignmentSuggestConsultantsWorkflow
                    //AssignmentSuggestConsultantsDebugWorkflow
                    case "SUGGEST_CONSULTANTS" -> new AssignmentSuggestConsultantsbyCodeWorkflow(toolClient)
                            .run(cmd.assignmentId(), 5);
                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(routeKey, RoutingWorkflow.ROUTES.get("other"));
                        yield new DomainWorkflow(toolClient).run(domainPrompt, input);
                    }
                };

                LOG.info("Answer:\n" + answer);
                return answer;
            }

            if ("consultant".equals(routeKey)) {
                ConsultantIntentClassifier classifier = new ConsultantIntentClassifier(routingClient);
                var cmd = classifier.classify(input);

                LOG.info("Consultant Action: " + cmd.action());

                String answer = switch (cmd.action()) {
                    case "LIST_ALL" -> new ConsultantListWorkflow(toolClient).run();
                    case "GET_BY_ID" -> new ConsultantDetailByIdWorkflow(toolClient).run(cmd.consultantId());
                    case "GET_BY_NAME" -> new ConsultantDetailByNameWorkflow(toolClient).run(cmd.firstName(), cmd.lastName());
                    case "FIND_AVAILABLE_BY_DATE" -> new ConsultantAvailableByDateWorkflow(toolClient)
                            .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    case "LIST_SICK_BY_DATE" -> new ConsultantSickByDateWorkflow(toolClient)
                            .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(routeKey, RoutingWorkflow.ROUTES.get("other"));
                        yield new DomainWorkflow(toolClient).run(domainPrompt, input);
                    }
                };

                LOG.info("Answer:\n" + answer);
                return answer;
            }

            if ("service".equals(routeKey)) {
                ServiceIntentClassifier classifier = new ServiceIntentClassifier(routingClient);
                var cmd = classifier.classify(input);

                LOG.info("Service Action: " + cmd.action());

                String answer = switch (cmd.action()) {
                    case "GET_SERVICES_BY_ID" -> new ConsultantServicesByIdWorkflow(toolClient).run(cmd.consultantId());
                    case "GET_SERVICES_BY_NAME" -> new ConsultantServicesByNameWorkflow(toolClient).run(cmd.firstName(), cmd.lastName());
                    case "FIND_CONSULTANTS_BY_SERVICES" -> new ConsultantsByServicesWorkflow(toolClient).run(cmd.services(), cmd.matchMode());
                    case "LIST_ALL_SERVICES" -> new ServiceListWorkflow(toolClient).run();
                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(routeKey, RoutingWorkflow.ROUTES.get("other"));
                        yield new DomainWorkflow(toolClient).run(domainPrompt, input);
                    }
                };

                LOG.info("Answer:\n" + answer);
                return answer;
            }

            // Other domains
            String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(routeKey, RoutingWorkflow.ROUTES.get("other"));
            String answer = new DomainWorkflow(toolClient).run(domainPrompt, input);
            LOG.info("Answer:\n" + answer);
            return answer;

        } catch (Exception e) {
            LOG.error("Error during execution", e);
            String answer = "An error occurred while processing the request.";
            LOG.info("Answer:\n" + answer);
            return answer;
        }
    }
}
