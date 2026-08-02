#!/bin/bash
set -e
# ============================================================
# Apboa 数据库增量迁移脚本
# 支持操作：status | migrate | baseline | repair <脚本名>
# 原理：通过 db_upgrade 台账表记录 sql/incremental 下脚本的
#       执行状态，migrate 时仅执行未记录的脚本（按文件名字典序）
# 注意：脚本必须保持 LF 换行
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

INCREMENTAL_DIR="$SCRIPT_DIR/../sql/incremental"
MYSQL_CONTAINER="apboa-mysql"

# ==================== 环境加载 ====================
load_env() {
  if [ -f ".env" ]; then
    set -a
    . ./.env
    set +a
  fi
  MYSQL_HOST="${MYSQL_HOST:-apboa-mysql}"
  MYSQL_PORT="${MYSQL_PORT:-3306}"
  MYSQL_DATABASE="${MYSQL_DATABASE:-apboa_next}"
  MYSQL_USER="${MYSQL_USER:-root}"
  MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-root}}"
  DOCKER_REGISTRY="${DOCKER_REGISTRY:-}"
}

# ==================== mysql 客户端封装 ====================
# 宿主机无需安装 mysql：优先复用本机 apboa-mysql 容器，
# 否则用一次性 mysql 客户端容器连接远程库（console 节点场景）
detect_mysql_mode() {
  if docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
    MYSQL_MODE="exec"
  elif docker ps -a --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER"; then
    # 容器存在但未运行：回退 run 模式会因无法解析容器名而报晦涩错误，直接拒绝
    echo "错误：容器 $MYSQL_CONTAINER 存在但未运行，请先启动后再执行迁移" >&2
    exit 1
  else
    MYSQL_MODE="run"
  fi
}

# 从 stdin 读取 SQL 并在目标库执行
mysql_exec() {
  if [ "$MYSQL_MODE" = "exec" ]; then
    docker exec -i -e MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CONTAINER" \
      mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" "$@"
  else
    docker run --rm -i -e MYSQL_PWD="$MYSQL_PASSWORD" "${DOCKER_REGISTRY}mysql:8.0" \
      mysql --default-character-set=utf8mb4 -h"$MYSQL_HOST" -P"$MYSQL_PORT" \
      -u"$MYSQL_USER" "$MYSQL_DATABASE" "$@"
  fi
}

# 执行查询并输出裸结果（无表头、制表符分隔）
mysql_query() {
  echo "$1" | mysql_exec -N -B
}

# ==================== 台账表 ====================
ensure_table() {
  mysql_exec <<'EOF'
CREATE TABLE IF NOT EXISTS `db_upgrade` (
  `script_name` varchar(200) NOT NULL COMMENT '脚本文件名',
  `checksum` char(32) DEFAULT NULL COMMENT '文件MD5',
  `success` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否执行成功',
  `executed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  `execution_ms` int DEFAULT NULL COMMENT '执行耗时毫秒',
  `note` varchar(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`script_name`)
) COMMENT='增量脚本执行台账';
EOF
}

# 文件名白名单校验，防止拼接进 SQL 造成注入
validate_name() {
  case "$1" in
    *[!A-Za-z0-9_.-]*|"")
      echo "错误：非法脚本文件名：$1（仅允许字母、数字、_ . -）" >&2
      exit 1
      ;;
  esac
}

file_md5() {
  md5sum "$1" | awk '{print $1}'
}

# 当前毫秒时间戳：%3N 为 GNU 扩展，非 GNU 环境下回退到秒级精度
now_ms() {
  local ms
  ms=$(date +%s%3N)
  case "$ms" in
    *[!0-9]*) ms=$(( $(date +%s) * 1000 )) ;;
  esac
  echo "$ms"
}

# 查询指定脚本的台账状态，输出 success 值（空串表示无记录）
record_status() {
  mysql_query "SELECT success FROM db_upgrade WHERE script_name='$1';"
}

record_checksum() {
  mysql_query "SELECT IFNULL(checksum,'') FROM db_upgrade WHERE script_name='$1';"
}

# ==================== 操作函数 ====================
do_status() {
  ensure_table
  local applied=0 pending=0 failed=0
  echo "台账状态（目录：sql/incremental）："
  for f in "$INCREMENTAL_DIR"/*.sql; do
    [ -e "$f" ] || continue
    local name status
    name=$(basename "$f")
    validate_name "$name"
    status=$(record_status "$name")
    if [ "$status" = "1" ]; then
      echo "  [已执行] $name"
      applied=$((applied + 1))
    elif [ "$status" = "0" ]; then
      echo "  [失败]   $name  （请人工处理后执行 repair）"
      failed=$((failed + 1))
    else
      echo "  [待执行] $name"
      pending=$((pending + 1))
    fi
  done
  echo ""
  echo "汇总：已执行 $applied，待执行 $pending，失败 $failed"
}

do_migrate() {
  ensure_table

  # 存在失败记录时禁止继续，避免带病升级
  local failed_list
  failed_list=$(mysql_query "SELECT script_name FROM db_upgrade WHERE success=0;")
  if [ -n "$failed_list" ]; then
    echo "错误：存在执行失败的脚本，请先人工处理并执行 repair：" >&2
    echo "$failed_list" | sed 's/^/  - /' >&2
    exit 1
  fi

  local executed=0 skipped=0
  for f in "$INCREMENTAL_DIR"/*.sql; do
    [ -e "$f" ] || continue
    local name status md5
    name=$(basename "$f")
    validate_name "$name"
    md5=$(file_md5 "$f")
    status=$(record_status "$name")

    if [ "$status" = "1" ]; then
      # 已执行：校验历史脚本是否被篡改（基线记录 checksum 为空则跳过校验）
      local recorded
      recorded=$(record_checksum "$name")
      if [ -n "$recorded" ] && [ "$recorded" != "$md5" ]; then
        echo "警告：$name 内容与执行时不一致（已发布脚本不应修改）"
      fi
      skipped=$((skipped + 1))
      continue
    fi

    echo ">> 执行 $name ..."
    local start_ms end_ms cost_ms rc
    start_ms=$(now_ms)
    ERR_FILE=$(mktemp)
    set +e
    mysql_exec < "$f" 2>"$ERR_FILE"
    rc=$?
    set -e
    end_ms=$(now_ms)
    cost_ms=$((end_ms - start_ms))

    if [ "$rc" -eq 0 ]; then
      # 台账写入失败时必须明确提示：脚本已生效，直接重跑会重复执行非幂等 SQL
      if ! mysql_query "INSERT INTO db_upgrade (script_name, checksum, success, execution_ms, note) VALUES ('$name', '$md5', 1, $cost_ms, 'migrate');"; then
        echo "错误：$name 已执行成功，但台账写入失败，请人工补录后再继续（切勿直接重跑 migrate）：" >&2
        echo "  INSERT INTO db_upgrade (script_name, checksum, success, note) VALUES ('$name', '$md5', 1, 'manual');" >&2
        exit 1
      fi
      echo "   完成（${cost_ms}ms）"
      executed=$((executed + 1))
      rm -f "$ERR_FILE"
      ERR_FILE=""
    else
      # fail-fast：记录失败并立即中止，不再执行后续脚本
      mysql_query "INSERT INTO db_upgrade (script_name, checksum, success, execution_ms, note) VALUES ('$name', '$md5', 0, $cost_ms, 'migrate failed');"
      echo "错误：$name 执行失败，迁移中止。mysql 错误输出：" >&2
      cat "$ERR_FILE" >&2
      echo "人工修复数据库后执行：bash upgrade-db.sh repair $name" >&2
      exit 1
    fi
  done
  echo ""
  echo "迁移完成：本次执行 $executed 个脚本，跳过 $skipped 个"
}

do_baseline() {
  ensure_table
  local count
  count=$(mysql_query "SELECT COUNT(*) FROM db_upgrade;")
  if [ "$count" != "0" ]; then
    echo "错误：台账表已有 $count 条记录，baseline 仅用于存量环境首次接入" >&2
    exit 1
  fi
  for f in "$INCREMENTAL_DIR"/*.sql; do
    [ -e "$f" ] || continue
    local name md5
    name=$(basename "$f")
    validate_name "$name"
    md5=$(file_md5 "$f")
    mysql_query "INSERT INTO db_upgrade (script_name, checksum, success, note) VALUES ('$name', '$md5', 1, 'manual baseline');"
    echo "  [已标记] $name"
  done
  echo "baseline 完成：以上脚本已全部标记为已执行（未实际执行 SQL）"
}

do_repair() {
  local name="$1"
  if [ -z "$name" ]; then
    echo "用法: $0 repair <脚本文件名>" >&2
    exit 1
  fi
  validate_name "$name"
  ensure_table
  local status
  status=$(record_status "$name")
  if [ "$status" != "0" ]; then
    echo "错误：$name 不存在失败记录，无需 repair" >&2
    exit 1
  fi
  mysql_query "DELETE FROM db_upgrade WHERE script_name='$name' AND success=0;"
  echo "$name 的失败记录已清除，可重新执行 migrate"
}

# ==================== 帮助信息 ====================
show_help() {
  echo "用法: $0 <操作>"
  echo ""
  echo "Apboa 数据库增量迁移脚本"
  echo ""
  echo "操作："
  echo "  status           查看增量脚本执行状态（已执行/待执行/失败）"
  echo "  migrate          按序执行所有未执行的增量脚本"
  echo "  baseline         存量环境首次接入：将当前所有脚本标记为已执行（不实际执行）"
  echo "  repair <脚本名>   清除指定脚本的失败记录，供修复后重跑"
  echo ""
  echo "说明："
  echo "  连接信息取自当前目录 .env（MYSQL_HOST/MYSQL_PORT/MYSQL_DATABASE/MYSQL_USER/MYSQL_PASSWORD）"
  echo "  本机存在 apboa-mysql 容器时直接复用，否则启动一次性 mysql 客户端容器连接远程库"
}

# ==================== 主逻辑 ====================
# 临时文件统一在退出时清理，避免异常路径泄漏
ERR_FILE=""
trap '[ -n "$ERR_FILE" ] && rm -f "$ERR_FILE" || true' EXIT

load_env
detect_mysql_mode

case "${1:-}" in
  status)   do_status  ;;
  migrate)  do_migrate ;;
  baseline) do_baseline ;;
  repair)   do_repair "${2:-}" ;;
  *)        show_help  ;;
esac
