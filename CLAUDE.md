# AI Agent Portfolio Coding Guide

## Comment Style

- Write comments in Chinese for business intent, integration boundaries, fallback behavior, and non-obvious tradeoffs.
- Do not comment obvious JavaBean getters/setters, imports, simple assignments, or framework annotations unless they carry project-specific meaning.
- Prefer class/module doc comments for service boundaries, DTO contracts, Agent nodes, data import scripts, and infrastructure clients.
- For generated code, include a short class/function doc comment when the code introduces a public API, a persistence model, an Agent node, or a cross-service call.
- Keep comments useful for interviews and handoff: explain why this code exists in the RAG/Agent architecture, not what each syntax line does.

## Project Conventions

- Java backend code stays in `backend-java/`; run Java commands from that directory with plain Maven.
- Python AI orchestration stays in `ai-service/`; shared script utilities stay in `shared/`.
- Frontend code stays in `frontend/`; use React + Vite and keep all browser API calls relative to `/api`.
- Local debug uses `.env` with `localhost`; Docker Compose uses `.env.docker` with service names.
- In Docker, frontend static files are served by Nginx and `/api` is proxied to `backend-java:8080`.
- Keep degradation paths explicit: mock model, dry-run import, Qdrant/Neo4j/MySQL fallback, and MCP tool fallback should all remain documented.
- When adding new endpoints or scripts, include one concise doc comment describing the request flow and where it fits in the demo.
