package org.example.mcpserver.config;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.example.mcpserver.service.*;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.stream.Stream;

@Configuration
public class ToolConfig {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> tools(
            AssignmentService assignmentService,
            AvailabilityService availabilityService,
            ConsultantService consultantService,
            ConsultantNoteService consultantNoteService,
            CustomerService customerService,
            PoolService poolService,
            RegionService regionService,
            ServiceService serviceService
    ) {
        List<ToolCallback> callbacks = new ArrayList<>();

        Stream.of(
                assignmentService,
                availabilityService,
                consultantService,
                consultantNoteService,
                customerService,
                poolService,
                regionService,
                serviceService
        ).forEach(svc -> callbacks.addAll(Arrays.asList(
                MethodToolCallbackProvider.builder().toolObjects(svc).build().getToolCallbacks()
        )));

        callbacks.sort(Comparator.comparing(cb -> cb.getToolDefinition().name()));

        // Debug: ska vara alfabetiskt i loggen
        callbacks.forEach(cb -> System.out.println("TOOL: " + cb.getToolDefinition().name()));

        return McpToolUtils.toSyncToolSpecifications(callbacks.toArray(new ToolCallback[0]));
    }
}