# MCP Client

![Java](https://img.shields.io/badge/Java-21+-blue) ![Spring
Boot](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen) ![Spring
AI](https://img.shields.io/badge/Spring_AI-MCP-orange)
![Maven](https://img.shields.io/badge/build-Maven-red)

------------------------------------------------------------------------

## Overview

**MCP Client** is a Spring Boot REST application that connects to an MCP
Server using Spring AI and the Model Context Protocol (MCP).

The application:

-   Starts a REST service
-   Accepts chat requests over HTTP
-   Routes/classifies requests and calls MCP tools when needed
-   Returns the formatted answer in the HTTP response

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

# REST API

Base URL:

http://localhost:8080

Endpoint:

POST /api/chat

Request body:

```json
{
  "message": "Visa uppgifter om konsult CONS_100001?",
  "prompt": "",
  "conversationId": "demo-session-1"
}
```

Response body:

```text
ConsultantId  FirstName  LastName   EmploymentType  Services                   Regions   Restrictions
CONS_100001   Anna       Eriksson   Employee         InventoryControl;Picker    SE-MAL
```

Notes:

-   `conversationId` is used for chat memory/context between requests.
-   If `conversationId` is omitted or blank, default conversation is used.

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
REST Response

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

Stop the running Spring Boot process with Ctrl+C.

------------------------------------------------------------------------

## License

Specify your license here (MIT, Apache 2.0, etc).
