#!/usr/bin/env bash
# ===========================================================================
# Agent 项目服务管理脚本
# 用法: ./scripts/service.sh <command> [service]
#
# Commands:
#   start   - 启动服务
#   stop    - 停止服务
#   restart - 重启服务
#   status  - 查看所有服务状态
#
# Services:
#   all      - 所有服务 (默认)
#   infra    - Docker 基础设施 (MySQL/Redis/Qdrant/Neo4j)
#   ai       - AI 服务 (端口 8000)
#   mcp      - MCP 服务 (端口 8100)
#   java     - Java 后端 (端口 8080)
#   frontend - 前端 (端口 5173)
#
# 示例:
#   ./scripts/service.sh status              # 查看所有服务状态
#   ./scripts/service.sh restart all          # 重启所有服务
#   ./scripts/service.sh restart java         # 只重启 Java 后端
#   ./scripts/service.sh stop ai              # 只停 AI 服务
#   ./scripts/service.sh start ai mcp         # 启动 AI 和 MCP 服务
# ===========================================================================

set -euo pipefail

# ---------- 项目根路径 ----------
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"
COMPOSE_FILE="$PROJECT_DIR/infra/docker-compose.yml"
ENV_DOCKER="$PROJECT_DIR/.env.docker"

# ---------- Python ----------
PYTHON="/opt/miniconda3/bin/python"

# ---------- 日志文件 ----------
LOG_DIR="${PROJECT_DIR}/logs"
mkdir -p "$LOG_DIR"

# ---------- 颜色输出 ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()    { echo -e "${GREEN}[✓]${NC} $1"; }
warn()    { echo -e "${YELLOW}[!]${NC} $1"; }
error()   { echo -e "${RED}[✗]${NC} $1"; }
header()  { echo -e "\n${CYAN}━━━ $1 ━━━${NC}"; }
section() { echo -e "\n${CYAN}═══ $1 ═══${NC}"; }

# ---------- 工具函数 ----------

# 查找占用端口的 PID
pid_by_port() {
  local port="$1"
  lsof -ti "tcp:$port" -sTCP:LISTEN 2>/dev/null || true
}

# 显示用：只取第一个 PID
first_pid_by_port() {
  pid_by_port "$1" | head -1
}

# 等待端口变为可用（等待进程停止）
wait_port_free() {
  local port="$1"
  local timeout="${2:-10}"
  for ((i=0; i<timeout; i++)); do
    if ! lsof -ti "tcp:$port" -sTCP:LISTEN >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# 等待端口开始监听（等待服务启动）
wait_port_listen() {
  local port="$1"
  local timeout="${2:-30}"
  for ((i=0; i<timeout; i++)); do
    if lsof -ti "tcp:$port" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# 加载 .env 环境变量
load_env() {
  set -a
  source "$ENV_FILE" 2>/dev/null || true
  set +a
}

# ---------- 服务管理函数 ----------

# ----- Docker 基础设施 -----
status_infra() {
  header "Docker 基础设施"
  if docker info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | grep -E "agent-|NAMES" || warn "没有运行中的 agent 容器"
  else
    error "Docker 未运行"
  fi
}

start_infra() {
  section "启动 Docker 基础设施"
  cd "$PROJECT_DIR"
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_DOCKER" up -d mysql redis qdrant neo4j
  info "Docker 基础设施已启动"
}

stop_infra() {
  section "停止 Docker 基础设施"
  cd "$PROJECT_DIR"
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_DOCKER" stop mysql redis qdrant neo4j
  info "Docker 基础设施已停止"
}

restart_infra() {
  stop_infra
  start_infra
}

# ----- AI 服务 (端口 8000) -----
status_ai() {
  local pid
  pid=$(first_pid_by_port 8000)
  if [ -n "$pid" ]; then
    if curl -sf http://127.0.0.1:8000/api/health >/dev/null 2>&1; then
      info "AI 服务 (端口 8000, PID $pid)  — $(curl -s http://127.0.0.1:8000/api/health)"
    else
      warn "AI 服务进程存在 (PID $pid) 但未响应"
    fi
  else
    error "AI 服务未运行"
  fi
}

start_ai() {
  section "启动 AI 服务 (端口 8000)"
  if [ -n "$(pid_by_port 8000)" ]; then
    warn "AI 服务已在运行，跳过 (使用 restart 或先 stop)"
    return 0
  fi
  load_env
  cd "$PROJECT_DIR/ai-service"
  nohup "$PYTHON" -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload \
    > "$LOG_DIR/ai-service.log" 2>&1 &
  local pid=$!
  sleep 3
  if wait_port_listen 8000 10; then
    info "AI 服务已启动 (PID $pid)"
  else
    error "AI 服务启动失败，查看日志: $LOG_DIR/ai-service.log"
    tail -5 "$LOG_DIR/ai-service.log"
  fi
}

stop_ai() {
  section "停止 AI 服务"
  local pids
  pids=$(pid_by_port 8000)
  if [ -n "$pids" ]; then
    # uvicorn --reload 会有一个 reloader 父进程和一个 worker 子进程，全部杀掉
    echo "$pids" | xargs kill 2>/dev/null || true
    if wait_port_free 8000 10; then
      info "AI 服务已停止"
    else
      warn "AI 服务未正常停止，强制终止"
      echo "$pids" | xargs kill -9 2>/dev/null || true
    fi
  else
    warn "AI 服务未运行"
  fi
}

restart_ai() {
  stop_ai
  sleep 1
  start_ai
}

# ----- MCP 服务 (端口 8100) -----
status_mcp() {
  local pid
  pid=$(first_pid_by_port 8100)
  if [ -n "$pid" ]; then
    if curl -sf http://127.0.0.1:8100/api/health >/dev/null 2>&1; then
      info "MCP 服务 (端口 8100, PID $pid)  — $(curl -s http://127.0.0.1:8100/api/health)"
    else
      warn "MCP 服务进程存在 (PID $pid) 但未响应"
    fi
  else
    error "MCP 服务未运行"
  fi
}

start_mcp() {
  section "启动 MCP 服务 (端口 8100)"
  if [ -n "$(pid_by_port 8100)" ]; then
    warn "MCP 服务已在运行，跳过 (使用 restart 或先 stop)"
    return 0
  fi
  load_env
  cd "$PROJECT_DIR/mcp-server"
  nohup "$PYTHON" -m uvicorn app.main:app --host 127.0.0.1 --port 8100 --reload \
    > "$LOG_DIR/mcp-server.log" 2>&1 &
  local pid=$!
  sleep 3
  if wait_port_listen 8100 10; then
    info "MCP 服务已启动 (PID $pid)"
  else
    error "MCP 服务启动失败，查看日志: $LOG_DIR/mcp-server.log"
    tail -5 "$LOG_DIR/mcp-server.log"
  fi
}

stop_mcp() {
  section "停止 MCP 服务"
  local pids
  pids=$(pid_by_port 8100)
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill 2>/dev/null || true
    if wait_port_free 8100 10; then
      info "MCP 服务已停止"
    else
      warn "MCP 服务未正常停止，强制终止"
      echo "$pids" | xargs kill -9 2>/dev/null || true
    fi
  else
    warn "MCP 服务未运行"
  fi
}

restart_mcp() {
  stop_mcp
  sleep 1
  start_mcp
}

# ----- Java 后端 (端口 8080) -----
status_java() {
  local pid
  pid=$(first_pid_by_port 8080)
  if [ -n "$pid" ]; then
    if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
      info "Java 后端 (端口 8080, PID $pid)  — $(curl -s http://127.0.0.1:8080/actuator/health)"
    else
      warn "Java 进程存在 (PID $pid) 但未响应"
    fi
  else
    error "Java 后端未运行"
  fi
}

start_java() {
  section "启动 Java 后端 (端口 8080)"
  if [ -n "$(pid_by_port 8080)" ]; then
    warn "Java 后端已在运行，跳过 (使用 restart 或先 stop)"
    return 0
  fi
  load_env
  cd "$PROJECT_DIR/backend-java"
  # 多模块后只把启动层打成可执行 Jar，避免非启动模块参与 spring-boot:run。
  mvn -q -pl agent-boot -am -DskipTests package
  nohup java -jar agent-boot/target/agent-boot-1.1.0.jar \
    > "$LOG_DIR/java-backend.log" 2>&1 &
  local pid=$!
  info "Java 编译启动中 (PID $pid)，首次需下载依赖约 1-2 分钟..."
  local waited=0
  while [ $waited -lt 90 ]; do
    if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
      info "Java 后端已启动 (耗时 ${waited}s)"
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  error "Java 后端启动超时，查看日志: $LOG_DIR/java-backend.log"
  tail -10 "$LOG_DIR/java-backend.log"
}

stop_java() {
  section "停止 Java 后端"
  local pids
  pids=$(pid_by_port 8080)
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill 2>/dev/null || true
    if wait_port_free 8080 15; then
      info "Java 后端已停止"
    else
      warn "Java 后端未正常停止，强制终止"
      echo "$pids" | xargs kill -9 2>/dev/null || true
    fi
  else
    # 也可能有 mvn 进程残留
    local mvn_pid
    mvn_pid=$(pgrep -f "spring-boot:run" 2>/dev/null || true)
    if [ -n "$mvn_pid" ]; then
      kill "$mvn_pid" 2>/dev/null || true
      info "Maven 进程已清理"
    else
      warn "Java 后端未运行"
    fi
  fi
}

restart_java() {
  stop_java
  sleep 1
  start_java
}

# ----- 前端 (端口 5173) -----
status_frontend() {
  local pid
  pid=$(first_pid_by_port 5173)
  if [ -n "$pid" ]; then
    if curl -sf -o /dev/null -w "" http://127.0.0.1:5173/ 2>/dev/null; then
      info "前端 (端口 5173, PID $pid) — 响应正常"
    else
      warn "前端进程存在 (PID $pid) 但未响应"
    fi
  else
    error "前端未运行"
  fi
}

start_frontend() {
  section "启动前端 (端口 5173)"
  if [ -n "$(pid_by_port 5173)" ]; then
    warn "前端已在运行，跳过 (使用 restart 或先 stop)"
    return 0
  fi
  cd "$PROJECT_DIR/frontend"
  nohup npm run dev -- --host 127.0.0.1 --port 5173 \
    > "$LOG_DIR/frontend.log" 2>&1 &
  local pid=$!
  sleep 3
  if wait_port_listen 5173 15; then
    info "前端已启动 (PID $pid)  → http://127.0.0.1:5173"
  else
    error "前端启动失败，查看日志: $LOG_DIR/frontend.log"
    tail -5 "$LOG_DIR/frontend.log"
  fi
}

stop_frontend() {
  section "停止前端"
  local pids
  pids=$(pid_by_port 5173)
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill 2>/dev/null || true
    if wait_port_free 5173 10; then
      info "前端已停止"
    else
      warn "前端未正常停止，强制终止"
      echo "$pids" | xargs kill -9 2>/dev/null || true
    fi
  else
    warn "前端未运行"
  fi
}

restart_frontend() {
  stop_frontend
  sleep 1
  start_frontend
}

# ---------- 全量操作 ----------

start_all() {
  section "=== 启动所有服务 ==="
  start_infra
  start_ai
  start_mcp
  start_java
  start_frontend
  section "=== 全部启动完成 ==="
  show_status_summary
}

stop_all() {
  section "=== 停止所有服务 ==="
  stop_frontend
  stop_java
  stop_mcp
  stop_ai
  stop_infra
  info "所有服务已停止"
}

restart_all() {
  section "=== 重启所有服务 ==="
  stop_frontend
  stop_java
  stop_mcp
  stop_ai
  start_ai
  start_mcp
  start_java
  start_frontend
  section "=== 全部重启完成 ==="
  show_status_summary
}

# ---------- 状态总览 ----------

show_status_summary() {
  echo ""
  header "服务状态总览"
  echo ""

  # 检查 Docker
  if docker info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
    info "Docker 运行中"
  else
    error "Docker 未运行"
  fi

  echo ""

  # 逐个服务状态（简洁版）
  for svc in infra ai mcp java frontend; do
    case "$svc" in
      infra)
        local names
        names=$(docker ps --format '{{.Names}}' 2>/dev/null | grep agent- | tr '\n' ' ')
        if [ -n "$names" ]; then
          info "基础设施 [${names}]"
        else
          error "基础设施 (Docker 容器未运行)"
        fi
        ;;
      ai)     status_ai ;;
      mcp)    status_mcp ;;
      java)   status_java ;;
      frontend) status_frontend ;;
    esac
  done

  echo ""
  header "访问入口"
  echo -e "  前端页面 ${GREEN}http://127.0.0.1:5173${NC}"
  echo -e "  AI 服务  ${GREEN}http://127.0.0.1:8000/docs${NC}"
  echo -e "  MCP 服务 ${GREEN}http://127.0.0.1:8100/docs${NC}"
  echo -e "  Java 后端 ${GREEN}http://127.0.0.1:8080/actuator/health${NC}"
  echo ""
  header "日志文件"
  echo -e "  ${YELLOW}$LOG_DIR/ai-service.log${NC}"
  echo -e "  ${YELLOW}$LOG_DIR/mcp-server.log${NC}"
  echo -e "  ${YELLOW}$LOG_DIR/java-backend.log${NC}"
  echo -e "  ${YELLOW}$LOG_DIR/frontend.log${NC}"
}

# ---------- 命令分发 ----------

COMMAND="${1:-status}"
shift || true

# 如果没有指定 services，默认为 "all"
if [ $# -eq 0 ]; then
  SERVICES=("all")
else
  SERVICES=("$@")
fi

# 校验命令
case "$COMMAND" in
  start|stop|restart|status) ;;
  *)
    echo "用法: $0 <start|stop|restart|status> [service...]"
    echo ""
    sed -n '3,19p' "$0"
    exit 1
    ;;
esac

# 执行
for svc in "${SERVICES[@]}"; do
  case "$svc" in
    all)
      case "$COMMAND" in
        start)   start_all ;;
        stop)    stop_all ;;
        restart) restart_all ;;
        status)  show_status_summary ;;
      esac
      ;;
    infra)
      case "$COMMAND" in
        start)   start_infra ;;
        stop)    stop_infra ;;
        restart) restart_infra ;;
        status)  status_infra ;;
      esac
      ;;
    ai)
      case "$COMMAND" in
        start)   start_ai ;;
        stop)    stop_ai ;;
        restart) restart_ai ;;
        status)  status_ai ;;
      esac
      ;;
    mcp)
      case "$COMMAND" in
        start)   start_mcp ;;
        stop)    stop_mcp ;;
        restart) restart_mcp ;;
        status)  status_mcp ;;
      esac
      ;;
    java)
      case "$COMMAND" in
        start)   start_java ;;
        stop)    stop_java ;;
        restart) restart_java ;;
        status)  status_java ;;
      esac
      ;;
    frontend)
      case "$COMMAND" in
        start)   start_frontend ;;
        stop)    stop_frontend ;;
        restart) restart_frontend ;;
        status)  status_frontend ;;
      esac
      ;;
    *)
      error "未知服务: $svc (可用: all, infra, ai, mcp, java, frontend)"
      ;;
  esac
done
