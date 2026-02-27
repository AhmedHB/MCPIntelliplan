package org.example.mcpserver.config;

import org.example.mcpserver.service.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider assignmentTools(AssignmentService service) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(service)
                .build();
    }

    @Bean
    public ToolCallbackProvider availabilityTools(AvailabilityService service) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(service)
                .build();
    }

    @Bean
    public ToolCallbackProvider consultantTools(ConsultantService service) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(service)
                .build();
    }

    @Bean
    public ToolCallbackProvider consultantNoteTools(ConsultantNoteService service) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(service)
                .build();
    }

    @Bean
    public ToolCallbackProvider customerTools(CustomerService service) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(service)
                .build();
    }

    @Bean
    public ToolCallbackProvider poolTools(PoolService service) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(service)
                .build();
    }

    @Bean
    public ToolCallbackProvider regionTools(RegionService service) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(service)
                .build();
    }

    @Bean
    public ToolCallbackProvider serviceTools(ServiceService service) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(service)
                .build();
    }
}
