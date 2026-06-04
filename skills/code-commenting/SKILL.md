---
name: code-commenting
description: Use when adding or reviewing code comments for this AI Agent Portfolio project, especially Java Spring Boot, Python FastAPI Agent/RAG code, ingestion scripts, and cross-service integration boundaries.
---

# Code Commenting

Use this skill to keep generated comments consistent with the project.

## Rules

- Write comments in Chinese.
- Explain business intent, Agent/RAG architecture role, external integration boundaries, fallback behavior, and production upgrade points.
- Avoid comments for obvious syntax, getters/setters, imports, and simple assignments.
- Prefer JavaDoc/docstrings on public classes, DTOs, controllers, services, Agent nodes, clients, and scripts.
- Keep comments concise enough that the code still reads cleanly.

## Patterns

- Java service/controller: describe the request flow and what service owns the real work.
- DTO/domain object: describe the table/API contract and any fields that are temporary demo defaults.
- Python Agent node: describe the node responsibility, state changes, and why the fallback exists.
- Ingestion script: describe what data sink it writes to and how dry-run behavior works.
- Infrastructure client: describe why the project uses lightweight HTTP clients instead of heavier SDKs.
