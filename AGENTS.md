# AI Agent Portfolio Coding Guide

## Project Map

- `backend-java/`: Spring Boot Java backend. Run Java commands from this directory with Maven.
- `ai-service/`: Python FastAPI service for RAG, agent orchestration, retrieval, citations, and evaluation.
- `frontend/`: React + Vite operations console. Keep browser API calls relative to `/api`.
- `mcp-server/`: FastAPI-based MCP-style tool server used by the demo environment.
- `shared/`: Shared Python script utilities.
- `infra/`: Docker Compose and database initialization assets.
- `datasets/`: Demo knowledge base, logs, tickets, and evaluation data.
- `docs/`: Architecture, module, and operation guides.

## Common Commands

- Java build from `backend-java/`: `mvn -pl agent-boot -am -DskipTests package`
- Frontend dev from `frontend/`: `npm run dev`
- Frontend build from `frontend/`: `npm run build`
- Frontend tests from `frontend/`: `npm run test`
- Python tests from `ai-service/`: `.venv/bin/python -m pytest`

## Comment Style

- Write comments in Chinese for business intent, integration boundaries, fallback behavior, and non-obvious tradeoffs.
- Do not comment obvious JavaBean getters/setters, imports, simple assignments, or framework annotations unless they carry project-specific meaning.
- Prefer class/module doc comments for service boundaries, DTO contracts, Agent nodes, data import scripts, and infrastructure clients.
- For generated code, include a short class/function doc comment when the code introduces a public API, a persistence model, an Agent node, or a cross-service call.
- Keep comments useful for interviews and handoff: explain why this code exists in the RAG/Agent architecture, not what each syntax line does.

## Project Conventions

- Local debug uses `.env` with `localhost`; Docker Compose uses `.env.docker` with service names.
- In Docker, frontend static files are served by Nginx and `/api` is proxied to `backend-java:8080`.
- Keep degradation paths explicit: mock model, dry-run import, Qdrant/Neo4j/MySQL fallback, and MCP tool fallback should all remain documented.
- When adding new endpoints or scripts, include one concise doc comment describing the request flow and where it fits in the demo.
- Keep Java module boundaries clear: API controllers in `agent-api`, application services and DTOs in `agent-application`, persistence and external clients in `agent-infrastructure`, domain entities in `agent-domain`, and boot wiring in `agent-boot`.
- Avoid broad refactors while making feature changes; preserve existing fallback behavior unless the task explicitly changes it.

## Change Checklist

- Read only the files needed for the current task; use `rg`/`rg --files` before opening broad sections of the repository.
- Run the narrowest relevant verification for the touched area when feasible.
- If tests or services cannot be run locally, state that clearly in the final handoff.
