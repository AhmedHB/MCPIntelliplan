# MCP Client

![Java](https://img.shields.io/badge/Java-21+-blue) ![Spring
Boot](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen) ![Spring
AI](https://img.shields.io/badge/Spring_AI-MCP-orange)
![Maven](https://img.shields.io/badge/build-Maven-red)

------------------------------------------------------------------------

## Overview

**MCP Client** is a Spring Boot CLI application that connects to an MCP
Server using Spring AI and the Model Context Protocol (MCP).

The application:

-   Starts
-   Executes a configured scenario
-   Prints the result in the console
-   Shuts itself down

It does NOT expose a web server.

All deterministic business logic lives in the MCP Server.

------------------------------------------------------------------------

# ⚠ Prerequisites

This client assumes:

-   MCP Server is running on:

    http://localhost:9090

-   The MCP Server database is up and running (via Docker)

-   The MCP Server SSE endpoint is available:

    http://localhost:9090/sse

If the server or database is not running, tool calls will fail.

------------------------------------------------------------------------

# Runtime Modes

The client supports two profiles:

## 🖥 Local Profile (Ollama)

Runs against a locally running Ollama model.

Start:

mvn spring-boot:run -Plocal

Requires Ollama running on:

http://localhost:11434

------------------------------------------------------------------------

## ☁ Cloud Profile (OpenAI)

Runs against OpenAI models.

### Configure API Key

Edit:

cloud.env.properties

Add:

OPENAI_API_KEY=your_api_key_here

⚠ Never commit your real API key.

Start:

mvn spring-boot:run -Pcloud

------------------------------------------------------------------------

# How a Sentence Is Processed (Agent Flow)

The MCP Client does NOT directly call tools immediately.

Instead, each user sentence goes through the following pipeline:

1️⃣ Routing (No Tools) - `RoutingWorkflow` - Determines domain
(assignment, consultant, service, etc.) - Uses LLM classification -
Returns a route key only

2️⃣ Intent Classification (No Tools) - Example:
`AssignmentIntentClassifier` - Converts sentence into structured JSON -
Extracts: - action - assignmentId - date - status - name

3️⃣ Agent / Tool Execution Phase - Only AFTER routing & classification -
`ChatClient` is created with ToolCallbackProvider - MCP tool is invoked
via SSE - Tool executes on MCP Server - Structured result is returned

4️⃣ Response Formatting - Sub-workflow formats output as plain text
table - Console prints result - Application shuts down

------------------------------------------------------------------------

# Agent Calls (MCP Tool Invocation)

When a tool is needed:

-   The client sends a structured MCP tool call
-   MCP Server executes deterministic business logic
-   Result is returned via SSE stream
-   Client formats and prints the result

The LLM does NOT compute business logic. It only:

-   Routes
-   Classifies
-   Formats

All validation and matching logic lives in the MCP Server.

------------------------------------------------------------------------

# Manual Scenario Configuration (Important)

The client currently runs a manually selected scenario inside:

McpClientApplication.java

Example:

@Bean public CommandLineRunner scenarioLLM(ChatClient.Builder
chatClientBuilder, ToolCallbackProvider tools,
ConfigurableApplicationContext context) {

    return args -> {
        String input = assignmentInput(0);
        //String input = consultantInput(5);
        //String input = serviceInput(7);
        //String input = customerInput();
        //String input = organisationInput();
        //String input = otherInput();

        ChatRequest chatRequest = new ChatRequest(input, "");
        String answer = chatService.chat(chatRequest);

        context.close();
    };

}

You must manually select which scenario to run by
commenting/uncommenting input lines.

Only one scenario runs per execution.

------------------------------------------------------------------------

# Architecture Overview

User Input\
↓\
Routing (LLM, no tools)\
↓\
Intent Classification (LLM, no tools)\
↓\
Tool Invocation (MCP via SSE)\
↓\
MCP Server (Business Logic + Database)\
↓\
Structured Result\
↓\
Formatted Console Output

------------------------------------------------------------------------

# Design Principle

The MCP Client:

-   Interprets natural language
-   Routes intent
-   Formats responses

The MCP Server:

-   Executes business rules
-   Validates data
-   Performs deterministic matching
-   Talks to the database

------------------------------------------------------------------------

# Stop the Application

The application exits automatically after executing the configured
scenario.

------------------------------------------------------------------------

## License

Specify your license here (MIT, Apache 2.0, etc).
