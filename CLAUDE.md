# AI Agent Portfolio Coding Guide

## Project Map

- `backend-java/`: Spring Boot 3.3.5 + Java 21 backend. 5 modules: domain → infrastructure → application → api → boot.
- `ai-service/`: Python FastAPI service for RAG agent orchestration, hybrid retrieval, and trace persistence.
- `frontend/`: React 18 + Vite 5 + React Router 7 operations console. Keep browser API calls relative to `/api`.
- `shared/`: Python utilities for embedding, entity extraction, MySQL/Qdrant/Neo4j clients.
- `infra/`: Docker Compose and database initialization assets.
- `datasets/`: Evaluation fixtures and importable knowledge documents.
- `scripts/`: Document ingestion, smoke test, and local service scripts.
- `ops/monitoring/`: Prometheus and Grafana dashboards, alert rules, and provisioning.
- `docs/`: Architecture, module, and operation guides.

## Common Commands

- Run all services: `make up`
- Run locally: `make infra-up && make run-ai && make run-java && make run-frontend`
- Java build: `cd backend-java && mvn -pl agent-boot -am -DskipTests package`
- Python tests: `cd ai-service && python -m pytest tests`
- Java tests: `cd backend-java && mvn test`
- Frontend dev: `cd frontend && npm run dev`
- Frontend build: `cd frontend && npm run build`
- Ingest documents: `make ingest-a`
- Smoke test: `make smoke`
- Alert webhook smoke test: `make smoke-alert`

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
- Current chat UI uses REST JSON through `POST /api/chat/messages`; do not document or add `/api/chat/messages/stream` unless the stream endpoint is actually implemented.
- Current online retrieval order is Qdrant semantic search, then Neo4j GraphRAG fallback, then `NO_EVIDENCE`. MySQL stores metadata, sessions, messages, status, and trace data; it is not the current online keyword fallback.
- Observability lives in this repository through Java `/actuator/prometheus`, Python `/metrics`, and Grafana webhook integration with adjacent `ai-incident-copilot`. Do not describe a local MCP diagnosis server as implemented here.
- Keep Java module boundaries clear: API controllers in `agent-api`, application services and DTOs in `agent-application`, persistence and external clients in `agent-infrastructure`, domain entities in `agent-domain`, and boot wiring in `agent-boot`.
- **Degradation policy**: Missing LLM credentials and failed vector writes must **fail loudly** — only explicitly requested dry-run/test paths may skip external writes. No silent "mock model" fallback.
- When adding new endpoints or scripts, include one concise doc comment describing the request flow and where it fits in the RAG/Agent architecture.
- Avoid broad refactors while making feature changes; preserve existing fallback behavior unless the task explicitly changes it.

## Change Checklist

- Read only the files needed for the current task; use `rg` / `rg --files` before opening broad sections of the repository.
- Run the narrowest relevant verification for the touched area when feasible (unit test, compilation, or import check).
- If tests or services cannot be run locally, state that clearly in the final handoff.
