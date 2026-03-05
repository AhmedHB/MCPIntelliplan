package org.example.mcpclient.config;

import org.example.mcpclient.tools.PrefixFilteringToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DomainToolProvidersConfig {

    @Bean
    @Qualifier("assignmentTools")
    ToolCallbackProvider assignmentTools(@Qualifier("mcpToolCallbacks") ToolCallbackProvider mcpTools) {
        return new PrefixFilteringToolCallbackProvider(mcpTools, List.of("assignment_"));
    }

    @Bean
    @Qualifier("organizationTools")
    ToolCallbackProvider organizationTools(@Qualifier("mcpToolCallbacks") ToolCallbackProvider mcpTools) {
        return new PrefixFilteringToolCallbackProvider(mcpTools, List.of("region_", "organization_"));
    }

    @Bean
    @Qualifier("consultantTools")
    ToolCallbackProvider consultantTools(@Qualifier("mcpToolCallbacks") ToolCallbackProvider mcpTools) {
        return new PrefixFilteringToolCallbackProvider(mcpTools, List.of("consultant_", "availability_"));
    }
}