# Makefile 用来把常用命令固定下来，减少本地开发和部署时的手工步骤。

COMPOSE_FILE=infra/docker-compose.yml
ENV_FILE?=.env.docker
PYTHON?=python3

status:
	@echo "=== Docker 容器状态 ==="
	@docker compose -f $(COMPOSE_FILE) ps --format "table {{.Name}}\t{{.Status}}"
	@echo ""
	@echo "=== HTTP 服务健康检查 ==="
	@printf "  backend-java  : "; curl -sf http://localhost:8080/actuator/health | grep -q UP && echo "UP  (port 8080)" || echo "DOWN"
	@printf "  ai-service    : "; curl -sf http://localhost:8000/docs > /dev/null 2>&1 && echo "UP  (port 8000)" || echo "DOWN"
	@printf "  frontend      : "; curl -sf http://localhost:3000/ > /dev/null 2>&1 && echo "UP  (port 3000)" || echo "DOWN"

up:
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) up -d

down:
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) down

logs:
	docker compose -f $(COMPOSE_FILE) --env-file $(ENV_FILE) logs -f

infra-up:
	@echo "中间件已独立到 ai-agent-infra-stack，请在 ../ai-agent-infra-stack 执行: docker compose up -d"

run-ai:
	cd ai-service && export $$(grep -v '^#' ../.env | xargs) && python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload

run-java:
	./scripts/service.sh restart java

build-java:
	cd backend-java && mvn -pl agent-boot -am -DskipTests clean package

run-frontend:
	cd frontend && npm run dev -- --host 127.0.0.1 --port 5173

install-frontend:
	cd frontend && npm install --cache .npm-cache

ingest-a:
	$(PYTHON) scripts/ingest_docs.py --kb-id 1 --source-dir datasets/kb_docs --rebuild

smoke:
	bash scripts/smoke_test.sh

smoke-alert:
	bash scripts/send_grafana_alert_smoke.sh

test-python:
	cd ai-service && python -m pytest tests

test-java:
	cd backend-java && mvn test

test-frontend:
	cd frontend && npm test -- --run

test: test-python test-java test-frontend
