# MCPIntelliplan

![Java](https://img.shields.io/badge/Java-21+-blue) ![Spring
Boot](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen) ![Spring
AI](https://img.shields.io/badge/Spring_AI-MCP-orange)
![Docker](https://img.shields.io/badge/Docker-required-blue)
![Maven](https://img.shields.io/badge/build-Maven-red)

------------------------------------------------------------------------

## 🚀 Overview

**MCPIntelliplan** is a structured AI-driven planning system built on
the Model Context Protocol (MCP).

The solution consists of:

-   🖥 Frontend (React) -- Web UI for planning workflows and user interaction
-   🧠 MCP Client -- LLM-powered routing, intent classification and tool
    orchestration
-   ⚙️ MCP Server -- Deterministic business logic and validation layer
-   🗄 Database (PostgreSQL) -- Persistent structured data storage

The system demonstrates how to combine Large Language Models with
deterministic backend logic in a clean, enterprise-style architecture.

------------------------------------------------------------------------

# 🏗 System Architecture

User\
↓\
Frontend (React)\
↓\
MCP Client (LLM: Routing + Intent Classification)\
↓\
MCP Tool Invocation (SSE)\
↓\
MCP Server (Business Logic + Validation)\
↓\
Database (PostgreSQL)

------------------------------------------------------------------------

## 🎯 Design Principles

-   LLM interprets natural language
-   Server executes business rules
-   No business logic inside the LLM
-   Deterministic matching and validation
-   Clear separation of responsibilities
-   Enterprise-ready architecture

------------------------------------------------------------------------

# 📦 Project Structure

MCPIntelliplan/\
├── Intelliplan/ (React frontend)\
├── MCPServer/ (Spring Boot MCP backend)\
├── MCPClient/ (Spring Boot LLM client)\
├── docker-compose.yml\
└── README.md

------------------------------------------------------------------------

# 🖥 Frontend (React)

The frontend application lives in `Intelliplan/` and provides the web UI.

Run:

cd Intelliplan\
npm install\
npm start

Default URL:

http://localhost:3000

------------------------------------------------------------------------

# 🧠 MCP Client

The MCP Client:

-   Routes user input to correct domain
-   Classifies intent into structured commands
-   Invokes MCP tools via SSE
-   Formats responses
-   Runs as CLI application (no web server)

Supports two runtime modes:

Local Mode (Ollama) mvn spring-boot:run -Plocal

Cloud Mode (OpenAI) mvn spring-boot:run -Pcloud

Requires: - MCP Server running on http://localhost:9090 - Database
running (via Docker)

------------------------------------------------------------------------

# ⚙️ MCP Server

The MCP Server exposes business logic as MCP tools:

-   Consultant management
-   Assignment handling
-   Availability tracking
-   Service (skill) management
-   Deterministic consultant suggestion engine

Run:

docker-compose up -d mvn spring-boot:run

SSE Endpoint:

http://localhost:9090/sse

------------------------------------------------------------------------

# 🔎 MCP Inspector

To inspect tools and debug MCP communication:

npx @modelcontextprotocol/inspector

Connect to:

http://localhost:9090/sse

------------------------------------------------------------------------

# 🧪 Example Flow

User: "Föreslå konsulter till uppdraget ASSIGN_300009"

1.  Client routes to Assignment domain
2.  Intent classifier extracts assignmentId
3.  MCP tool assignment_suggest_consultants is invoked
4.  Server executes deterministic matching
5.  Result returned and formatted

------------------------------------------------------------------------

# 🔐 Security Notes

-   API keys are stored in cloud.env.properties (ignored by Git)
-   Never commit real secrets
-   Use .env.example pattern for safe configuration

------------------------------------------------------------------------

# 🚀 How to Run Everything

1.  Start database\
    docker-compose up -d

2.  Start MCP Server\
    cd MCPServer\
    mvn spring-boot:run

3.  Start MCP Client\
    cd MCPClient\
    mvn spring-boot:run -Plocal

4.  Start Frontend\
    cd Intelliplan\
    npm install\
    npm start

------------------------------------------------------------------------

# 📌 Summary

MCPIntelliplan demonstrates:

-   Structured AI architecture
-   MCP tool-based orchestration
-   Clean separation of LLM and business logic
-   Enterprise-style modular design
-   Secure configuration handling

------------------------------------------------------------------------

## 📄 License

Specify license here (MIT, Apache 2.0, etc).
