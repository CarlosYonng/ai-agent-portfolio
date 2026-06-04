# Makefile 用来把常用命令固定下来，面试演示时不用临场记命令。

COMPOSE_FILE=infra/docker-compose.yml
ENV_FILE?=.env.docker
PYTHON?=python3

up:
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) up -d

down:
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) down

logs:
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) logs -f

infra-up:
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) up -d mysql redis qdrant neo4j

run-ai:
	cd ai-service && set -a && . ../.env && set +a && python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload

run-mcp:
	cd mcp-server && set -a && . ../.env && set +a && python -m uvicorn app.main:app --host 127.0.0.1 --port 8100 --reload

run-java:
	cd backend-java && set -a && . ../.env && set +a && mvn spring-boot:run

run-frontend:
	cd frontend && npm run dev -- --host 127.0.0.1 --port 5173

install-frontend:
	cd frontend && npm install --cache .npm-cache

ingest-a:
	$(PYTHON) scripts/ingest_docs.py --kb-id 1 --source-dir datasets/kb_docs --rebuild

ingest-b:
	$(PYTHON) scripts/ingest_code.py --repo-dir datasets/demo-order-service --service order
	$(PYTHON) scripts/ingest_logs.py --log-dir datasets/logs --service order
	$(PYTHON) scripts/ingest_tickets.py --ticket-dir datasets/tickets --service order

eval:
	$(PYTHON) scripts/run_rag_eval.py --dataset datasets/eval/kb_eval.jsonl --top-k 5

smoke:
	bash scripts/smoke_test.sh

test-python:
	cd ai-service && python -m pytest tests

test-java:
	cd backend-java && mvn test

test-frontend:
	cd frontend && npm test -- --run

test: test-python test-java test-frontend
