#!/bin/bash
set -e
# ============================================================
# Apboa 单机模式管理脚本
# 支持操作：build | rebuild | start | stop | restart | down | status
# 注意：脚本必须保持 LF 换行
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose-simple.yml"
ENV_FILE=".env.simple"
SERVICE_NAME="Apboa 单机版"

# ==================== 宿主机信息检测 ====================
detect_host_info() {
  HOST_IP=$(hostname -I 2>/dev/null | awk '{print $1}')
  if [ -z "$HOST_IP" ]; then
    HOST_IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{print $7; exit}')
  fi
  if [ -z "$HOST_IP" ]; then
    echo "警告：无法自动检测宿主机 IP，使用 127.0.0.1"
    HOST_IP="127.0.0.1"
  fi
  HOST_NAME=$(hostname)

  export APBOA_NODE_ID=${APBOA_NODE_ID:-${HOST_IP}}
  export APBOA_HOST_NAME=${APBOA_HOST_NAME:-${HOST_NAME}}
  export APBOA_HOST_IP=${APBOA_HOST_IP:-${HOST_IP}}
}

# ==================== 环境初始化 ====================
init_env() {
  detect_host_info
  if [ -f "$ENV_FILE" ]; then
    cp "$ENV_FILE" .env
  fi
}

# ==================== 操作函数 ====================
do_build() {
  echo ">> 构建并启动 ${SERVICE_NAME}..."
  init_env
  docker compose -f "$COMPOSE_FILE" up -d --build
  echo ""
  echo "${SERVICE_NAME} 启动完成，访问 http://localhost:${FRONTEND_PORT:-80}"
}

do_rebuild() {
  echo ">> 停止并删除容器，然后重新构建..."
  init_env
  docker compose -f "$COMPOSE_FILE" down
  docker compose -f "$COMPOSE_FILE" up -d --build
  echo ""
  echo "${SERVICE_NAME} 重建完成"
}

do_start() {
  echo ">> 启动 ${SERVICE_NAME}..."
  init_env
  docker compose -f "$COMPOSE_FILE" start
  echo "${SERVICE_NAME} 已启动"
}

do_stop() {
  echo ">> 停止 ${SERVICE_NAME}..."
  docker compose -f "$COMPOSE_FILE" stop
  echo "${SERVICE_NAME} 已停止"
}

do_restart() {
  echo ">> 重启 ${SERVICE_NAME}..."
  docker compose -f "$COMPOSE_FILE" restart
  echo "${SERVICE_NAME} 已重启"
}

do_down() {
  echo ">> 停止并删除 ${SERVICE_NAME} 容器..."
  docker compose -f "$COMPOSE_FILE" down
  echo "${SERVICE_NAME} 容器已删除"
}

do_status() {
  docker compose -f "$COMPOSE_FILE" ps
}

# ==================== 帮助信息 ====================
show_help() {
  echo "用法: $0 <操作>"
  echo ""
  echo "${SERVICE_NAME} 管理脚本"
  echo ""
  echo "操作："
  echo "  build    构建镜像并启动所有服务"
  echo "  rebuild  停止并删除容器，然后重新构建并启动"
  echo "  start    启动已创建的服务"
  echo "  stop     停止正在运行的服务"
  echo "  restart  重启服务"
  echo "  down     停止并删除容器和网络"
  echo "  status   查看服务运行状态"
  echo ""
  echo "示例："
  echo "  $0 build     # 首次部署或更新代码后使用"
  echo "  $0 rebuild   # 需要完全重建时使用"
  echo "  $0 status    # 查看当前运行状态"
}

# ==================== 主逻辑 ====================
case "${1:-}" in
  build)   do_build   ;;
  rebuild) do_rebuild ;;
  start)   do_start   ;;
  stop)    do_stop    ;;
  restart) do_restart ;;
  down)    do_down    ;;
  status)  do_status  ;;
  *)       show_help  ;;
esac
