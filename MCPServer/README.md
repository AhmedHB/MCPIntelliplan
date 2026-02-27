# MCP Server

![Java](https://img.shields.io/badge/Java-21+-blue) ![Spring
Boot](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen)
![Maven](https://img.shields.io/badge/build-Maven-red)
![Docker](https://img.shields.io/badge/docker-required-blue)

## 🚀 Overview

**MCP Server** is a Spring Boot backend exposing structured business
functionality as AI Tools via the **Model Context Protocol (MCP)**.

It serves as the deterministic backend layer for an LLM-powered client.
All business logic and validation are executed on the server --- never
inside the LLM.

------------------------------------------------------------------------

## ⚡ Quick Start

### 1️⃣ Start Infrastructure (Docker)

``` bash
docker-compose up -d
```

### 2️⃣ Start MCP Server

``` bash
mvn spring-boot:run
```

Default server port:

    9090

------------------------------------------------------------------------

## 🔎 Inspect MCP Server (MCP Inspector)

You can inspect the MCP Server and its exposed tools using the official
MCP Inspector.

Run:

``` bash
npx @modelcontextprotocol/inspector
```

Then configure it to connect to:

    http://localhost:9090/sse

This allows you to:

-   View available tools
-   Trigger tool calls manually
-   Inspect tool schemas
-   Debug tool responses
-   Validate MCP connectivity

This is extremely useful during development and debugging.

------------------------------------------------------------------------

## 🧹 Reset Database (PostgreSQL)

If you need to completely reset the schema (for example during
development), run the following SQL inside your database.

⚠ This will delete ALL data.

``` sql
-- Close other sessions (so DROP SCHEMA works)
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = current_database()
  AND pid <> pg_backend_pid();

DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

------------------------------------------------------------------------

## 🧠 Consultant Suggestion Engine

Tool:

    assignment_suggest_consultants(assignmentId, limit)

Matching is:

-   Deterministic
-   Server-side validated
-   Exact token matching
-   Overlap-safe

------------------------------------------------------------------------

## 🛑 Stop the System

Stop server:

    Ctrl + C

Stop Docker:

``` bash
docker-compose down
```

------------------------------------------------------------------------

## 📄 License

Specify your license here (MIT, Apache 2.0, etc).
