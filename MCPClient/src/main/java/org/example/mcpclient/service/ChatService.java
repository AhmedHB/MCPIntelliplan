package org.example.mcpclient.service;

import org.example.mcpclient.dto.ChatRequest;
import org.example.mcpclient.workflow.*;
import org.example.mcpclient.workflow.subworkflow.assignment.*;
import org.example.mcpclient.workflow.subworkflow.consultant.*;
import org.example.mcpclient.workflow.subworkflow.customer.CustomerGetByIdWorkflow;
import org.example.mcpclient.workflow.subworkflow.customer.CustomerListWorkflow;
import org.example.mcpclient.workflow.subworkflow.customer.CustomerSearchWorkflow;
import org.example.mcpclient.workflow.subworkflow.organization.*;
import org.example.mcpclient.workflow.subworkflow.service.ConsultantServicesByIdWorkflow;
import org.example.mcpclient.workflow.subworkflow.service.ConsultantServicesByNameWorkflow;
import org.example.mcpclient.workflow.subworkflow.service.ConsultantsByServicesWorkflow;
import org.example.mcpclient.workflow.subworkflow.service.ServiceListWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
public class ChatService {
    private static final Logger LOG =
            LoggerFactory.getLogger(ChatService.class);

    // Build once, reuse
    private final ChatClient routingClient; // NO TOOLS
    private final ChatClient toolClient;    // WITH TOOLS

    public ChatService(ChatClient.Builder chatClientBuilder,
                       ToolCallbackProvider tools,
                       VectorStore vectorStore) {

        var memoryAdvisor = VectorStoreChatMemoryAdvisor.builder(vectorStore).build();

        // Routing client (NO TOOLS) – used for routing + intent classifiers
        this.routingClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor)
                .build();

        // Tool client (WITH TOOLS) – build ONCE so tools don't accumulate
        this.toolClient = chatClientBuilder
                .defaultToolCallbacks(tools)
                .defaultAdvisors(memoryAdvisor)
                .build();
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
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? ChatMemory.DEFAULT_CONVERSATION_ID
                : request.conversationId();

        try {
            LOG.info("User Input: {}", input);
            LOG.info("ConversationId: {}", conversationId);

            ChatClient requestRoutingClient = routingClient
                    .mutate()
                    .defaultAdvisors(spec -> spec
                            .param(ChatMemory.CONVERSATION_ID, conversationId)
                            .param(VectorStoreChatMemoryAdvisor.TOP_K, 10))
                    .build();

            ChatClient requestToolClient = toolClient
                    .mutate()
                    .defaultAdvisors(spec -> spec
                            .param(ChatMemory.CONVERSATION_ID, conversationId)
                            .param(VectorStoreChatMemoryAdvisor.TOP_K, 10))
                    .build();

            // Routing
            RoutingWorkflow routingWorkflow = new RoutingWorkflow(requestRoutingClient);
            String routeKey = routingWorkflow.route(input);

            LOG.info("Route: {}", routeKey);

            if ("assignment".equals(routeKey)) {
                AssignmentIntentClassifier classifier = new AssignmentIntentClassifier(requestRoutingClient);
                var cmd = classifier.classify(input);

                LOG.info("Assignment Action: {}", cmd.action());

                String answer = switch (cmd.action()) {
                    case "COUNT_BY_STATUS" ->
                            new AssignmentCountByStatusWorkflow(requestToolClient).run(cmd.status());

                    case "FIND_BY_STATUS" ->
                            new AssignmentFindByStatusWorkflow(requestToolClient).run(cmd.status());

                    case "COUNT_BY_DATE" ->
                            new AssignmentCountByDateWorkflow(requestToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "FIND_BY_DATE" ->
                            new AssignmentFindByDateWorkflow(requestToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "CONSULTANTS_ON_DATE" ->
                            new AssignmentFindConsultantsByDateWorkflow(requestToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "WORKING_ON_DATE" ->
                            new ConsultantWorkingOnDateWorkflow(requestToolClient)
                                    .run(cmd.name(), cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "SUGGEST_CONSULTANTS" ->
                            new AssignmentSuggestConsultantsbyCodeWorkflow(requestToolClient)
                                    .run(cmd.assignmentId(), 5);

                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                                routeKey,
                                RoutingWorkflow.ROUTES.get("other")
                        );
                        yield new DomainWorkflow(requestToolClient).run(domainPrompt, input);
                    }
                };

                LOG.info("Answer:\n{}", answer);
                return answer;
            }

            if ("consultant".equals(routeKey)) {
                ConsultantIntentClassifier classifier = new ConsultantIntentClassifier(requestRoutingClient);
                var cmd = classifier.classify(input);

                LOG.info("Consultant Action: {}", cmd.action());

                String answer = switch (cmd.action()) {
                    case "LIST_ALL" ->
                            new ConsultantListWorkflow(requestToolClient).run();

                    case "GET_BY_ID" ->
                            new ConsultantDetailByIdWorkflow(requestToolClient).run(cmd.consultantId());

                    case "GET_BY_NAME" ->
                            new ConsultantDetailByNameWorkflow(requestToolClient).run(cmd.firstName(), cmd.lastName());

                    case "FIND_AVAILABLE_BY_DATE" ->
                            new ConsultantAvailableByDateWorkflow(requestToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "LIST_SICK_BY_DATE" ->
                            new ConsultantSickByDateWorkflow(requestToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                                routeKey,
                                RoutingWorkflow.ROUTES.get("other")
                        );
                        yield new DomainWorkflow(requestToolClient).run(domainPrompt, input);
                    }
                };

                LOG.info("Answer:\n{}", answer);
                return answer;
            }

            if ("service".equals(routeKey)) {
                ServiceIntentClassifier classifier = new ServiceIntentClassifier(requestRoutingClient);
                var cmd = classifier.classify(input);

                LOG.info("Service Action: {}", cmd.action());

                String answer = switch (cmd.action()) {
                    case "GET_SERVICES_BY_ID" ->
                            new ConsultantServicesByIdWorkflow(requestToolClient).run(cmd.consultantId());

                    case "GET_SERVICES_BY_NAME" ->
                            new ConsultantServicesByNameWorkflow(requestToolClient).run(cmd.firstName(), cmd.lastName());

                    case "FIND_CONSULTANTS_BY_SERVICES" ->
                            new ConsultantsByServicesWorkflow(requestToolClient).run(cmd.services(), cmd.matchMode());

                    case "LIST_ALL_SERVICES" ->
                            new ServiceListWorkflow(requestToolClient).run();

                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                                routeKey,
                                RoutingWorkflow.ROUTES.get("other")
                        );
                        yield new DomainWorkflow(requestToolClient).run(domainPrompt, input);
                    }
                };

                LOG.info("Answer:\n{}", answer);
                return answer;
            }

            if ("customer".equals(routeKey)) {
                CustomerIntentClassifier classifier = new CustomerIntentClassifier(requestRoutingClient);
                var cmd = classifier.classify(input);

                LOG.info("Customer Action: {}", cmd.action());

                String answer = switch (cmd.action()) {
                    case "GET_BY_ID" ->
                            new CustomerGetByIdWorkflow(requestToolClient).run(cmd.customerId());

                    case "SEARCH" ->
                            new CustomerSearchWorkflow(requestToolClient).run(cmd.customerName(), cmd.region(), cmd.riskProfile());

                    case "LIST_ALL" ->
                            new CustomerListWorkflow(requestToolClient).run();

                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                                routeKey,
                                RoutingWorkflow.ROUTES.get("other")
                        );
                        yield new DomainWorkflow(requestToolClient).run(domainPrompt, input);
                    }
                };

                LOG.info("Answer:\n{}", answer);
                return answer;
            }

            if ("organization".equals(routeKey)) {
                OrganizationIntentClassifier classifier = new OrganizationIntentClassifier(requestRoutingClient);
                var cmd = classifier.classify(input);

                LOG.info("Organization Action: {}", cmd.action());

                String answer = switch (cmd.action()) {
                    case "GET_REGION_BY_CONSULTANT_ID" ->
                            new OrganizationGetRegionByConsultantIdWorkflow(requestToolClient)
                                    .run(cmd.consultantId());

                    case "GET_REGION_BY_CONSULTANT_NAME" ->
                            new OrganizationGetRegionByConsultantNameWorkflow(requestToolClient)
                                    .run(cmd.firstName(), cmd.lastName());

                    case "LIST_CONSULTANTS_BY_REGION" ->
                            new OrganizationListConsultantsByRegionWorkflow(requestToolClient)
                                    .run(cmd.region());

                    case "COUNT_CONSULTANTS_BY_REGION" ->
                            new OrganizationCountConsultantsByRegionWorkflow(requestToolClient)
                                    .run(cmd.region());

                    case "organization_count_consultants_by_region" ->
                            new OrganizationListRegionsWithCountsWorkflow(requestToolClient).run();

                    case "GET_REGION_WITH_MOST_CONSULTANTS" ->
                            new OrganizationGetRegionWithMostConsultantsWorkflow(requestToolClient).run();

                    case "LIST_REGIONS" ->
                            new OrganizationListRegionsWorkflow(requestToolClient).run();

                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                                routeKey,
                                RoutingWorkflow.ROUTES.get("other")
                        );
                        yield new DomainWorkflow(requestToolClient).run(domainPrompt, input);
                    }
                };

                LOG.info("Answer:\n{}", answer);
                return answer;
            }

            // Other domains
            String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                    routeKey,
                    RoutingWorkflow.ROUTES.get("other")
            );
            String answer = new DomainWorkflow(requestToolClient).run(domainPrompt, input);

            LOG.info("Answer:\n{}", answer);
            return answer;

        } catch (Exception e) {
            LOG.error("Error during execution", e);
            String answer = "An error occurred while processing the request.";
            LOG.info("Answer:\n{}", answer);
            return answer;
        }
    }
}
