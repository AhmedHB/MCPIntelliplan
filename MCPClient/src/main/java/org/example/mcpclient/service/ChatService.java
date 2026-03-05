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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatService {
    private static final Logger LOG =
            LoggerFactory.getLogger(ChatService.class);

    private final ToolCallbackProvider assignmentTools;
    private final ToolCallbackProvider organizationTools;
    private final ToolCallbackProvider consultantTools;
    private final ToolCallbackProvider mcpTools;

    // Build once, reuse
    private final ChatClient routingClient;      // NO TOOLS, WITH MEMORY
    private final ChatClient toolClient;         // WITH TOOLS, WITH MEMORY (för fria svar om du vill)

    @Value("${app.model.chat.options.top_k}")
    private int top_k;



    public ChatService(ChatClient.Builder chatClientBuilder,
                       @Qualifier("mcpToolCallbacks") ToolCallbackProvider mcpTools,
                       @Qualifier("assignmentTools") ToolCallbackProvider assignmentTools,
                       @Qualifier("organizationTools") ToolCallbackProvider organizationTools,
                       @Qualifier("consultantTools") ToolCallbackProvider consultantTools,
                       VectorStore vectorStore) {

        this.mcpTools = mcpTools;
        this.assignmentTools = assignmentTools;
        this.organizationTools = organizationTools;
        this.consultantTools = consultantTools;

        var memoryAdvisor = VectorStoreChatMemoryAdvisor.builder(vectorStore).build();

        this.routingClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor)
                .build();

        this.toolClient = chatClientBuilder.build(); // <-- inga tools här
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

        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? ChatMemory.DEFAULT_CONVERSATION_ID
                : request.conversationId();

        String languageSystemInstruction =
                "Respond in English. Keep identifiers, codes, and dates unchanged.";

        try {
            LOG.info("User Input: {}", input);
            LOG.info("ConversationId: {}", conversationId);

            // ROUTING: memory + global language (no tools needed here)
            ChatClient requestRoutingClient = routingClient
                    .mutate()
                    .defaultSystem(languageSystemInstruction)
                    .defaultAdvisors(spec -> spec
                            .param(ChatMemory.CONVERSATION_ID, conversationId)
                            .param(VectorStoreChatMemoryAdvisor.TOP_K, top_k))
                    .build();

            // Routing
            RoutingWorkflow routingWorkflow = new RoutingWorkflow(requestRoutingClient);
            String routeKey = routingWorkflow.route(input);

            LOG.info("Route: {}", routeKey);

            // Pick tool allowlist by route (fallback to ALL MCP tools)
            ToolCallbackProvider domainTools = switch (routeKey) {
                case "assignment" -> assignmentTools;                 // assignment_*
                case "organization" -> organizationTools;             // region_*, organization_*
                case "consultant" -> consultantTools;                 // consultant_*, availability_*
                default -> mcpTools;                                  // service/customer/other => all MCP tools
            };

            // STRICT domain tools (no memory advisors) - TOOLS SET EXACTLY ONCE
            ChatClient requestStrictDomainToolClient = toolClient.mutate()
                    .defaultSystem(languageSystemInstruction)
                    .defaultToolCallbacks(domainTools)
                    .build();

            // Domain tools + memory (only if you want free-form answers with memory)
            ChatClient requestDomainToolClientWithMemory = toolClient.mutate()
                    .defaultSystem(languageSystemInstruction)
                    .defaultToolCallbacks(domainTools)
                    .defaultAdvisors(spec -> spec
                            .param(ChatMemory.CONVERSATION_ID, conversationId)
                            .param(VectorStoreChatMemoryAdvisor.TOP_K, top_k))
                    .build();

            if ("assignment".equals(routeKey)) {
                AssignmentIntentClassifier classifier = new AssignmentIntentClassifier(requestRoutingClient);
                var cmd = classifier.classify(input);

                LOG.info("Assignment Action: {}", cmd.action());

                String answer = switch (cmd.action()) {
                    case "COUNT_BY_STATUS" ->
                            new AssignmentCountByStatusWorkflow(requestStrictDomainToolClient).run(cmd.status());

                    case "FIND_BY_STATUS" ->
                            new AssignmentFindByStatusWorkflow(requestStrictDomainToolClient).run(cmd.status());

                    case "COUNT_BY_DATE" ->
                            new AssignmentCountByDateWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "FIND_BY_DATE" ->
                            new AssignmentFindByDateWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "CONSULTANTS_ON_DATE" ->
                            new AssignmentFindConsultantsByDateWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "WORKING_ON_DATE" ->
                            new ConsultantWorkingOnDateWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.name(), cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "SUGGEST_CONSULTANTS" ->
                            new AssignmentSuggestConsultantsbyCodeWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.assignmentId(), 5);

                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                                routeKey,
                                RoutingWorkflow.ROUTES.get("other")
                        );
                        yield new DomainWorkflow(requestStrictDomainToolClient).run(domainPrompt, input);
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
                            new ConsultantListWorkflow(requestStrictDomainToolClient).run();

                    case "GET_BY_ID" ->
                            new ConsultantDetailByIdWorkflow(requestStrictDomainToolClient).run(cmd.consultantId());

                    case "GET_BY_NAME" ->
                            new ConsultantDetailByNameWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.firstName(), cmd.lastName());

                    case "FIND_AVAILABLE_BY_DATE" ->
                            new ConsultantAvailableByDateWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    case "LIST_SICK_BY_DATE" ->
                            new ConsultantSickByDateWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.date().format(DateTimeFormatter.ISO_LOCAL_DATE));

                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                                routeKey,
                                RoutingWorkflow.ROUTES.get("other")
                        );
                        yield new DomainWorkflow(requestStrictDomainToolClient).run(domainPrompt, input);
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
                            new OrganizationGetRegionByConsultantIdWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.consultantId());

                    case "GET_REGION_BY_CONSULTANT_NAME" ->
                            new OrganizationGetRegionByConsultantNameWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.firstName(), cmd.lastName());

                    case "LIST_CONSULTANTS_BY_REGION" ->
                            new OrganizationListConsultantsByRegionWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.region());

                    case "COUNT_CONSULTANTS_BY_REGION" ->
                            new OrganizationCountConsultantsByRegionWorkflow(requestStrictDomainToolClient)
                                    .run(cmd.region());

                    case "organization_count_consultants_by_region" ->
                            new OrganizationListRegionsWithCountsWorkflow(requestStrictDomainToolClient).run();

                    case "GET_REGION_WITH_MOST_CONSULTANTS" ->
                            new OrganizationGetRegionWithMostConsultantsWorkflow(requestStrictDomainToolClient).run();

                    case "LIST_REGIONS" ->
                            new OrganizationListRegionsWorkflow(requestStrictDomainToolClient).run();

                    default -> {
                        String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                                routeKey,
                                RoutingWorkflow.ROUTES.get("other")
                        );
                        yield new DomainWorkflow(requestStrictDomainToolClient).run(domainPrompt, input);
                    }
                };

                LOG.info("Answer:\n{}", answer);
                return answer;
            }

            // Other domains: keep your old behavior (memory + free-form)
            String domainPrompt = RoutingWorkflow.ROUTES.getOrDefault(
                    routeKey,
                    RoutingWorkflow.ROUTES.get("other")
            );

            String answer = new DomainWorkflow(requestDomainToolClientWithMemory).run(domainPrompt, input);

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
