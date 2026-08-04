Here is the complete English version of the project documentation, formatted as a clean, professional **`README.md`** file that you can use directly in your Git repository or project pitch.

---

# 📌 PROJECT SPECIFICATION & ARCHITECTURE OVERVIEW

## 🎯 1. PROJECT OVERVIEW

* **Project Name:** Enterprise Natural Language Database Agent
* **Objective:** Build an intelligent system that enables users to query databases using **natural language** (English). The system automatically parses user intent, retrieves relevant schema context, generates accurate SQL queries, enforces safety guardrails, executes read-only operations, and presents visual tabular data.
* **Timeline:** 2 Weeks (Iterative / Phased strategy).
* **Tech Stack:** Java (Spring Boot), LangChain4j, JDBC, Database (PostgreSQL / H2), LLM API (OpenAI / Gemini / Claude).

---

## 🛠️ 2. CORE FEATURES

1. **Dynamic Schema & Rules Retrieval (RAG):**
* **Token Optimization (80–90% Cost Reduction):** Uses Vector Search (RAG) to dynamically retrieve *only* the specific table schemas relevant to the query, rather than stuffing the entire database schema into the LLM context.
* Injects internal business logic/rules (e.g., *VIP Customer = total spend > $2,000*) so the LLM correctly interprets complex queries.


2. **Text-to-SQL Generation:** Translates natural language questions into precise SQL `SELECT` queries.
3. **Database Guardrails (Security & Safety):**
* Hard-blocks 100% of data-mutation or destructive queries (`DELETE`, `DROP`, `UPDATE`, `INSERT`, `ALTER`).
* Automatically enforces row limits (e.g., `LIMIT 100`) to prevent database hanging and memory overflow.


4. **Data Execution & Visual Result:** Safely executes SQL queries via JDBC and formats raw JSON results into intuitive markdown/tabular views on the UI.

---

## 📐 3. ARCHITECTURE SOLUTIONS & ROADMAP

### 🟢 PHASE 1: Centralized Integrated Architecture (Non-MCP) — *Primary Focus (Week 1)*

* **Model:** Combines Web UI, RAG Engine, Guardrails, and JDBC Tools within a **single Spring Boot Monolith** powered by **LangChain4j**.
* **Goal:** Ensures a 100% working, stable, and reliable deliverable on time for demo/submission.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ SPRING BOOT APPLICATION (MONOLITH)                                          │
│                                                                             │
│  [Web UI Chat] ──► [Agent Orchestrator] ──► [LangChain4j + AI Model API]    │
│                                                     │                       │
│                                    Direct Call      ▼                       │
│                                          [Java JDBC Tools & RAG Engine]     │
└─────────────────────────────────────────────────────┬───────────────────────┘
                                                      │ (JDBC)
                                                      ▼
                                           [ Database H2 / Postgres ]

```

---

### 🔵 PHASE 2: Standardized Plug-and-Play Architecture (MCP) — *Enhancement (Week 2)*

* **Model:** Decouples the RAG Engine, Guardrails, and JDBC Tools into an independent **Java MCP Server (Model Context Protocol)** adhering to Anthropic's open-source standard.
* **Goal:** Transforms the database into a "smart socket," enabling any MCP Client (your Web UI or the official **Claude Desktop app**) to connect instantly via plug-and-play without rewriting backend integration code.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. MCP CLIENT / HOST (Spring Boot Web UI OR Official Claude Desktop App)    │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ MCP Protocol (JSON-RPC)
┌──────────────────────────────────────▼──────────────────────────────────────┐
│ 2. JAVA DATABASE MCP SERVER (Independent Data Service)                      │
│    • Tool 1: get_schema_and_rules()  ──► [Embedded RAG Engine]             │
│    • Tool 2: execute_select_sql()    ──► [Guardrails + JDBC Engine]         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (JDBC)
                                       ▼
                            [ Database H2 / Postgres ]

```