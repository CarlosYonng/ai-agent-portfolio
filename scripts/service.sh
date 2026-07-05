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
#   ai       - AI 服务 (端口：.env 的 AI_SERVICE_PORT，默认 8000)
#   java     - Java 后端 (端口：.env 的 JAVA_PORT，默认 8080)
#   frontend - 前端 (端口 5173)
#
# 示例:
#   ./scripts/service.sh status              # 查看所有服务状态
#   ./scripts/service.sh restart all          # 重启所有服务
#   ./scripts/service.sh restart java         # 只重启 Java 后端
#   ./scripts/service.sh stop ai              # 只停 AI 服务
# ===========================================================================

set -euo pipefail

# ---------- 项目根路径 ----------
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$PROJECT_DIR/.env"

# ---------- Python ----------
# 默认使用 ai-service 自己的虚拟环境，避免系统 Python 和项目依赖不一致。
AI_SERVICE_PYTHON_DEFAULT="$PROJECT_DIR/ai-service/.venv/bin/python"

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

# 从 .env 读取服务端口，带默认值。
# Docker 单独部署端口不在脚本管理范围，这些端口只影响本地启动。
load_env
AI_SERVICE_PORT="${AI_SERVICE_PORT:-8000}"
JAVA_PORT="${JAVA_PORT:-8080}"

# ---------- 服务管理函数 ----------

# ----- AI 服务 (端口：.env 的 AI_SERVICE_PORT，默认 8000) -----
status_ai() {
  local pid
  pid=$(first_pid_by_port "$AI_SERVICE_PORT")
  if [ -n "$pid" ]; then
    if curl -sf "http://127.0.0.1:$AI_SERVICE_PORT/api/health" >/dev/null 2>&1; then
      info "AI 服务 (端口 $AI_SERVICE_PORT, PID $pid)  — $(curl -s "http://127.0.0.1:$AI_SERVICE_PORT/api/health")"
    else
      warn "AI 服务进程存在 (PID $pid) 但未响应"
    fi
  else
    error "AI 服务未运行"
  fi
}

start_ai() {
  section "启动 AI 服务 (端口 $AI_SERVICE_PORT)"
  if [ -n "$(pid_by_port "$AI_SERVICE_PORT")" ]; then
    warn "AI 服务已在运行，跳过 (使用 restart 或先 stop)"
    return 0
  fi
  load_env
  # load_env 会覆盖脚本顶部默认值；再次读取确保 .env 中的 AI_SERVICE_PORT 生效
  AI_SERVICE_PORT="${AI_SERVICE_PORT:-8000}"
  local python_bin="${AI_SERVICE_PYTHON:-$AI_SERVICE_PYTHON_DEFAULT}"
  if [ ! -x "$python_bin" ]; then
    error "AI Python 不存在或不可执行: $python_bin"
    error "请先创建虚拟环境，或在 .env 中设置 AI_SERVICE_PYTHON"
    return 1
  fi
  if ! "$python_bin" -c "import prometheus_client" >/dev/null 2>&1; then
    warn "AI 服务依赖不完整，正在安装 requirements.txt"
    "$python_bin" -m pip install -r "$PROJECT_DIR/ai-service/requirements.txt"
  fi
  local reload_arg=""
  if [ "${AI_SERVICE_RELOAD:-false}" = "true" ]; then
    reload_arg="--reload"
  fi
  cd "$PROJECT_DIR/ai-service"
  nohup "$python_bin" -m uvicorn app.main:app --host 127.0.0.1 --port "$AI_SERVICE_PORT" $reload_arg \
    > "$LOG_DIR/ai-service.log" 2>&1 &
  local pid=$!
  sleep 3
  if wait_port_listen "$AI_SERVICE_PORT" 10; then
    info "AI 服务已启动 (PID $pid)"
  else
    error "AI 服务启动失败，查看日志: $LOG_DIR/ai-service.log"
    tail -5 "$LOG_DIR/ai-service.log"
  fi
}

stop_ai() {
  section "停止 AI 服务"
  local pids
  pids=$(pid_by_port "$AI_SERVICE_PORT")
  if [ -n "$pids" ]; then
    # uvicorn --reload 会有一个 reloader 父进程和一个 worker 子进程，全部杀掉
    echo "$pids" | xargs kill 2>/dev/null || true
    if wait_port_free "$AI_SERVICE_PORT" 10; then
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

# ----- Java 后端 (端口：.env 的 JAVA_PORT，默认 8080) -----
status_java() {
  local pid
  pid=$(first_pid_by_port "$JAVA_PORT")
  if [ -n "$pid" ]; then
    if curl -sf "http://127.0.0.1:$JAVA_PORT/actuator/health" >/dev/null 2>&1; then
      info "Java 后端 (端口 $JAVA_PORT, PID $pid)  — $(curl -s "http://127.0.0.1:$JAVA_PORT/actuator/health")"
    else
      warn "Java 进程存在 (PID $pid) 但未响应"
    fi
  else
    error "Java 后端未运行"
  fi
}

start_java() {
  section "启动 Java 后端 (端口 $JAVA_PORT)"
  if [ -n "$(pid_by_port "$JAVA_PORT")" ]; then
    error "端口 $JAVA_PORT 已有 Java 后端进程，不能直接 start 覆盖。请使用 restart java，避免旧 Jar 继续接请求。"
    return 1
  fi
  load_env
  # load_env 会覆盖脚本顶部默认值；再次读取确保 .env 中的 JAVA_PORT 生效
  JAVA_PORT="${JAVA_PORT:-8080}"
  cd "$PROJECT_DIR/backend-java"
  # 通过命令行 --server.port 覆盖 application.yml 的默认值，优先级最高
  export JAVA_PORT
  # 包迁移/重命名后必须 clean，避免 target/classes 里残留旧 Controller 或 DTO。
  mvn -q -pl agent-boot -am -DskipTests clean package
  nohup java -jar agent-boot/target/agent-boot-1.1.0.jar --server.port="$JAVA_PORT" \
    > "$LOG_DIR/java-backend.log" 2>&1 &
  local pid=$!
  info "Java 编译启动中 (PID $pid)，首次需下载依赖约 1-2 分钟..."
  local waited=0
  while [ $waited -lt 90 ]; do
    if curl -sf "http://127.0.0.1:$JAVA_PORT/actuator/health" >/dev/null 2>&1; then
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
  pids=$(pid_by_port "$JAVA_PORT")
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill 2>/dev/null || true
    if wait_port_free "$JAVA_PORT" 15; then
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
  start_ai
  start_java
  start_frontend
  section "=== 全部启动完成 ==="
  show_status_summary
}

stop_all() {
  section "=== 停止所有服务 ==="
  stop_frontend
  stop_java
  stop_ai
  info "所有服务已停止"
}

restart_all() {
  section "=== 重启所有服务 ==="
  stop_frontend
  stop_java
  stop_ai
  start_ai
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
  for svc in ai java frontend; do
    case "$svc" in
      ai)     status_ai ;;
      java)   status_java ;;
      frontend) status_frontend ;;
    esac
  done

  echo ""
  header "访问入口"
  echo -e "  前端页面 ${GREEN}http://127.0.0.1:5173${NC}"
  echo -e "  AI 服务  ${GREEN}http://127.0.0.1:$AI_SERVICE_PORT/docs${NC}"
  echo -e "  Java 后端 ${GREEN}http://127.0.0.1:$JAVA_PORT/actuator/health${NC}"
  echo ""
  header "日志文件"
  echo -e "  ${YELLOW}$LOG_DIR/ai-service.log${NC}"
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
    ai)
      case "$COMMAND" in
        start)   start_ai ;;
        stop)    stop_ai ;;
        restart) restart_ai ;;
        status)  status_ai ;;
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
      error "未知服务: $svc (可用: all, ai, java, frontend)"
      ;;
  esac
done
