package org.example.mcpclient.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Arrays;
import java.util.List;

public class PrefixFilteringToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;
    private final List<String> allowedPrefixes;

    public PrefixFilteringToolCallbackProvider(ToolCallbackProvider delegate, List<String> allowedPrefixes) {
        this.delegate = delegate;
        this.allowedPrefixes = allowedPrefixes;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return Arrays.stream(delegate.getToolCallbacks())
                .filter(cb -> {
                    String name = cb.getToolDefinition().name();
                    return allowedPrefixes.stream().anyMatch(name::startsWith);
                })
                .toArray(ToolCallback[]::new);
    }
}